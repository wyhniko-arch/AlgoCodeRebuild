package com.algoblock.logic;

import com.algoblock.context.RuntimeContext;
import com.algoblock.lang.Lexer;
import com.algoblock.lang.StatementExecutor;
import com.algoblock.protocol.ResponseBuilder;
import com.algoblock.tools.buffer.RowBuffer;
import com.algoblock.tools.leveltree.LevelTree;
import com.algoblock.tools.savedata.Progress;
import com.algoblock.tools.savedata.SaveManager;

/**
 * 状态机门面：
 *   - 持有全局生命周期对象（Progress / LevelTree）
 *   - 持有当前 LevelState（关卡内才非空）
 *   - 唯一对外入口 interact()，内部 dispatch 把请求路由到各无状态模块
 *
 * 各无状态模块的职责分工：
 *   LevelLoader        关卡装填（解析 JSON、注册结构、应用配额、初始化指令）
 *   StatementExecutor  语句执行（语法/语义匹配、配额计数、调用结构方法）
 *   Lexer              词法分析（输入缓冲 → 补全候选 + 状态）
 *   InputHandler       输入处理（tab/del/enter/up/down/exit 与单字符）
 *   Browser            选关浏览（LevelTree 数据 → browse 响应；start:n 分发）
 *
 * RuntimeContext 仍持有 Logic 引用，通过 triggerEngineCommand() 回调隐式语句执行。
 * 该方法在此处保留为公共委托，实际逻辑下沉到 StatementExecutor。
 */
public class Logic {

    // ==========================================
    // 单例
    // ==========================================

    private static volatile Logic instance = null;

    private static Logic getInstance() {
        if (instance == null) {
            synchronized (Logic.class) {
                if (instance == null) instance = new Logic();
            }
        }
        return instance;
    }

    private Logic() {
        // 启动时一次性加载存档并建立选关浏览状态（停留在根目录）
        this.progress  = SaveManager.load();
        this.levelTree = new LevelTree(this.progress);
    }

    // ==========================================
    // 全局生命周期字段
    // ==========================================

    /** 玩家存档（解锁/通关数据），落盘到 saves/save.json。 */
    private Progress progress;

    /** 当前选关浏览状态，维护"当前所在路径"和"该层级可见节点"。 */
    private LevelTree levelTree;

    /** 当前关卡状态；非关卡内为 null（用 inLevel 判断更清晰）。 */
    private LevelState state;

    /** 是否身处关卡内。 */
    private boolean inLevel = false;

    // ==========================================
    // 唯一对外公开的方法
    // ==========================================

    /**
     * 状态机统一入口。所有响应均为单元素 String[]，内容是 JSON 字符串。
     *
     * 指令清单：
     *   全局（任何时候可用）：
     *     query:rowbuffer / query:rowbuffer:N
     *     action:ResetSaveData             清空存档（删除磁盘文件后落盘空 Progress）
     *
     *   选关界面（不在关卡时可用）：
     *     query:browse                     当前路径与可见节点
     *     action:start:<n>                 选择当前可见列表第 n 项（1-based 取模）
     *     action:back                      返回上一层
     *
     *   关卡内（已 action:start 一关后可用）：
     *     action:reset                     退出关卡回到选关
     *     action:input:<token>             字符 / tab / del / enter / up / down / exit
     *     query:nextcommandpart            词法补全
     *     query:objects                    存活对象快照
     *     query:levelinfo                  当前关卡信息
     */
    public static String[] interact(String command) {
        Logic logic = getInstance();
        synchronized (logic) {
            return logic.dispatch(command);
        }
    }

    // ==========================================
    // 分发
    // ==========================================

    private String[] dispatch(String command) {
        if (command == null || command.isBlank()) return new String[0];

        // ============ 全局指令 ============
        if (command.equalsIgnoreCase("query:rowbuffer")) {
            return ResponseBuilder.debug(RowBuffer.getRecent());
        }
        if (command.toLowerCase().startsWith("query:rowbuffer:")) {
            String tail = command.substring("query:rowbuffer:".length()).trim();
            try {
                return ResponseBuilder.debug(RowBuffer.getRecent(Integer.parseInt(tail)));
            } catch (NumberFormatException e) {
                return ResponseBuilder.error("query:rowbuffer:<N> 中 N 必须是整数");
            }
        }
        if (command.equalsIgnoreCase("action:ResetSaveData")) {
            // 重置存档：删除文件、回归空 Progress、把浏览状态重置到根
            this.progress  = SaveManager.reset();
            this.levelTree = new LevelTree(this.progress);
            clearLevelState(); // 防御性清空：哪怕在关卡内 reset 存档也直接踢回选关
            return ResponseBuilder.ack("存档已重置");
        }

        // ============ 选关界面指令（仅未在关卡时可用） ============
        if (!inLevel) {
            if (command.equalsIgnoreCase("query:browse")) {
                return Browser.buildBrowseResponse(levelTree);
            }
            if (command.equalsIgnoreCase("action:back")) {
                boolean moved = levelTree.back(progress);
                return moved ? Browser.buildBrowseResponse(levelTree)
                             : ResponseBuilder.ack("已在根目录");
            }
            if (command.toLowerCase().startsWith("action:start:")) {
                String tail = command.substring("action:start:".length()).trim();
                try {
                    return Browser.handleStart(this, levelTree, progress, Integer.parseInt(tail));
                } catch (NumberFormatException e) {
                    return ResponseBuilder.error("action:start:<n> 中 n 必须是整数");
                }
            }
            return ResponseBuilder.error("当前未在关卡中，可用：query:browse / action:start:<n> / action:back");
        }

        // ============ 关卡内指令 ============
        if (command.equalsIgnoreCase("action:reset")) return resetLevel();
        if (command.toLowerCase().startsWith("action:input:")) {
            return InputHandler.handle(this, state, command.substring("action:input:".length()));
        }
        if (command.equalsIgnoreCase("query:nextcommandpart")) {
            Lexer.InputAnalysis a = Lexer.compute(state);
            String status = a.isExactMatch ? "exact" : (a.isDeadEnd ? "deadend" : "partial");
            return ResponseBuilder.commandHints(
                    state.inputBuffer.toString(), status, state.selectedOptionIndex, a.optionsList);
        }
        if (command.equalsIgnoreCase("query:objects")) {
            return ResponseBuilder.objects(
                    state.runtimeContext.collectAllSnapshots(state.structidToStructure));
        }
        if (command.equalsIgnoreCase("query:levelinfo")) {
            return ResponseBuilder.levelInfo(
                    state.levelConfig.levelName,
                    state.levelConfig.story,
                    state.currentLevelPath,
                    state.currentStep,
                    state.levelConfig.stepsLimit,
                    state.runtimeContext.getBufferCommandIn(),
                    state.runtimeContext.getBufferCommandOut(),
                    state.levelConfig.inputDesc,
                    state.levelConfig.outputDesc);
        }
        return ResponseBuilder.error("未知指令: " + command);
    }

    // ==========================================
    // 关卡生命周期协调（被 Browser / InputHandler 反向调用）
    // ==========================================

    /**
     * 装填并启动一关。Browser.handleStart 在选关界面命中关卡时调用。
     */
    String[] startLevelByPathExternal(String levelPath) {
        clearLevelState();

        state = new LevelState();
        state.runtimeContext   = new RuntimeContext(this);
        state.currentLevelPath = levelPath;
        inLevel = true;

        LevelLoader.loadLevel(state, levelPath);
        LevelLoader.registerStructures(state);
        LevelLoader.initAllowedLimits(state);
        LevelLoader.buildCommandsByTag(state);
        LevelLoader.executeInitCommands(state);

        RowBuffer.append("\n--- 进入关卡主循环 (关卡 " + levelPath + ") ---");
        return ResponseBuilder.ack("关卡 " + levelPath + " 已加载，等待输入");
    }

    /**
     * InputHandler 在 exit token 时调用，与 action:reset 等价。
     */
    String[] resetLevelExternal() {
        return resetLevel();
    }

    /**
     * InputHandler 通关时调用：本关 cleared、递归冒泡所有满足 clear_when 的祖先层级，
     * 落盘，最后把浏览状态切回本关父层级（让玩家看到刚解锁的兄弟节点）。
     */
    void handleLevelClearedExternal() {
        LevelTree.applyClear(state.currentLevelPath, progress);
        SaveManager.save(progress);
        String parent = LevelTree.parentOf(state.currentLevelPath);
        clearLevelState();
        levelTree.enter(parent, progress);
    }

    /**
     * InputHandler 失败时调用：仅切回父层级，不写存档（步数耗尽不算通关）。
     */
    void handleLevelFailedExternal() {
        String parent = LevelTree.parentOf(state.currentLevelPath);
        clearLevelState();
        levelTree.enter(parent, progress);
    }

    /**
     * RuntimeContext 在结构方法内部回调本方法触发隐式命令（如 Queue.pop 后触发 buffer 的 push）。
     * 这是唯一保留在 Logic 上的"执行"入口，原因是 RuntimeContext 已持有 Logic 引用，
     * 改造为持有 StatementExecutor 反而增加耦合。实际逻辑下沉到 StatementExecutor。
     */
    public void triggerEngineCommand(String statement) {
        StatementExecutor.triggerEngineCommand(state, statement);
    }

    // ==========================================
    // 内部：关卡内的退出处理
    // ==========================================

    private String[] resetLevel() {
        clearLevelState();
        RowBuffer.append("\n[Info] 关卡已重置");
        // 退出关卡回到选关界面：刷新一下当前层级的可见性（存档可能在通关时变化）
        levelTree.enter(levelTree.getCurrentPath(), progress);
        return ResponseBuilder.ack("已退出关卡");
    }

    private void clearLevelState() {
        inLevel = false;
        if (state != null) state.clear();
        state = null;
    }
}