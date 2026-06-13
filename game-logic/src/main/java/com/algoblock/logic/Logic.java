package com.algoblock.logic;

import java.util.*;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.StructureMethod;
import com.algoblock.tools.jsonloader.analysis.LevelConfigLoader;
import com.algoblock.tools.jsonloader.namerule.LevelConfig;
import com.algoblock.tools.buffer.RowBuffer;
import com.algoblock.tools.leveltree.LevelTree;
import com.algoblock.tools.savedata.Progress;
import com.algoblock.tools.savedata.SaveManager;

import com.google.gson.JsonObject;


public class Logic {
    // ==========================================
    // 单例
    // ==========================================
    private static volatile Logic instance = null;
    private static Logic getInstance() {
        if (instance == null) {
            synchronized (Logic.class) {
                if (instance == null) {
                    instance = new Logic();
                }
            }
        }
        return instance;
    }

    private Logic() {
        // 启动时一次性加载存档并建立关卡树浏览状态（停留在根目录）
        this.progress  = SaveManager.load();
        this.levelTree = new LevelTree(this.progress);
    }

    // ==========================================
    // 全局（与 Logic 同生命周期）：存档 + 关卡树浏览
    // ==========================================
    /** 玩家存档（解锁/通关数据），落盘到 saves/save.json。 */
    private Progress progress;
    /** 当前选关浏览状态，维护"当前所在路径"和"该层级可见节点"。 */
    private LevelTree levelTree;

    // ==========================================
    // 关卡级字段（与关卡同生命周期）
    // ==========================================
    private RuntimeContext runtimeContext = null;
    private LevelConfig levelConfig = null;
    /** 当前正在玩的关卡的完整路径（如 "tutorial/basics/step01"）；非关卡内为 null。 */
    private String currentLevelPath = null;
    /** structId → Template 实例 */
    private Map<String, Abstract> structidToStructure = null;
    /** structId_commandId → CommandDefinition（快速定位） */
    private Map<String, CommandDefinition> struct_command_idToCommand = null;
    /** structId → CommandDefinition 列表（含 argHints / tags） */
    private Map<String, List<CommandDefinition>> struct_idToCommands = null;
    /**
     * 标签 → 该标签下所有有效 CommandDefinition 的集合。
     * 在 initAllowedLimits 结束后构建，供 ArgHintResolver 的 cmd[tag] 使用。
     */
    private Map<String, List<CommandDefinition>> commandsByTag = null;

    // 关卡内交互状态
    private StringBuilder inputBuffer = new StringBuilder();
    private int selectedOptionIndex = 0;
    private int currentStep = 0;
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
    // 内部分发
    // ==========================================

    private String[] dispatch(String command) {
        if (command == null || command.isBlank()) return new String[0];

        // ============ 全局指令（任何时候可用） ============
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
            this.progress = SaveManager.reset();
            this.levelTree = new LevelTree(this.progress);
            clearLevelState(); // 防御性清空：哪怕在关卡内 reset 存档也直接踢回选关
            return ResponseBuilder.ack("存档已重置");
        }

        // ============ 选关界面指令（仅未在关卡时可用） ============
        if (!inLevel) {
            if (command.equalsIgnoreCase("query:browse")) {
                return buildBrowseResponse();
            }
            if (command.equalsIgnoreCase("action:back")) {
                boolean moved = levelTree.back(progress);
                return moved ? buildBrowseResponse() : ResponseBuilder.ack("已在根目录");
            }
            if (command.toLowerCase().startsWith("action:start:")) {
                String tail = command.substring("action:start:".length()).trim();
                try {
                    int n = Integer.parseInt(tail);
                    return handleStart(n);
                } catch (NumberFormatException e) {
                    return ResponseBuilder.error("action:start:<n> 中 n 必须是整数");
                }
            }
            return ResponseBuilder.error("当前未在关卡中，可用：query:browse / action:start:<n> / action:back");
        }

        // ============ 关卡内指令 ============
        if (command.equalsIgnoreCase("action:reset"))         return resetLevel();
        if (command.toLowerCase().startsWith("action:input:"))
            return handleInput(command.substring("action:input:".length()));
        if (command.equalsIgnoreCase("query:nextcommandpart")) return queryNextCommandPart();
        if (command.equalsIgnoreCase("query:objects")) {
            return ResponseBuilder.objects(runtimeContext.collectAllSnapshots(structidToStructure));
        }
        if (command.equalsIgnoreCase("query:levelinfo")) {
            return ResponseBuilder.levelInfo(
                    levelConfig.levelName,
                    levelConfig.story,
                    currentLevelPath,
                    currentStep,
                    levelConfig.stepsLimit,
                    runtimeContext.getBufferCommandIn(),
                    runtimeContext.getBufferCommandOut(),
                    levelConfig.inputDesc,
                    levelConfig.outputDesc);
        }
        return ResponseBuilder.error("未知指令: " + command);
    }

    /** 把 LevelTree 当前可见节点映射成 ResponseBuilder.NodeView 并打包成 browse 响应。 */
    private String[] buildBrowseResponse() {
        List<LevelTree.VisibleNode> vis = levelTree.getVisibleNodes();
        List<ResponseBuilder.NodeView> views = new ArrayList<>();
        for (LevelTree.VisibleNode v : vis) {
            String typeStr = (v.type == LevelTree.NodeType.FOLDER) ? "folder" : "level";
            views.add(new ResponseBuilder.NodeView(v.name, typeStr, v.unlocked, v.cleared));
        }
        return ResponseBuilder.browse(levelTree.getCurrentPath(), views);
    }

    /**
     * action:start:<n> 实现。
     * 1-based 取模选可见节点。文件夹则进入返回新 browse；
     * 关卡则真正开始。选中锁住节点时报错（不会自动跳过——透明给前端，由前端决定提示语）。
     */
    private String[] handleStart(int n) {
        LevelTree.StartResult r = levelTree.start(n, progress);
        switch (r.kind) {
            case EMPTY:           return ResponseBuilder.error("当前层级无可见节点");
            case LOCKED:          return ResponseBuilder.error("节点尚未解锁: " + r.lockedName);
            case ENTERED_FOLDER:  return buildBrowseResponse();
            case STARTED_LEVEL:   return startLevelByPath(r.levelPath);
            default:              return ResponseBuilder.error("内部错误");
        }
    }
    

    
    // ==========================================
    // 关卡生命周期
    // ==========================================

    /**
     * 用关卡完整路径启动关卡。路径来自 LevelTree.start(n) 的返回。
     * 此函数本身不再做任何关卡可见性/解锁判定——可见性由 LevelTree 在 start(n) 前已过滤。
     */
    private String[] startLevelByPath(String levelPath) {
        clearLevelState();

        runtimeContext = new RuntimeContext(this);
        struct_command_idToCommand = new HashMap<>();
        struct_idToCommands = new HashMap<>();
        structidToStructure = new HashMap<>();
        commandsByTag = new HashMap<>();
        inputBuffer = new StringBuilder();
        selectedOptionIndex = 0;
        currentStep = 0;
        currentLevelPath = levelPath;
        inLevel = true;

        loadLevel(levelPath);
        registerStructures();
        initAllowedLimits();
        buildCommandsByTag();
        executeInitCommands();

        RowBuffer.append("\n--- 进入关卡主循环 (关卡 " + levelPath + ") ---");
        return ResponseBuilder.ack("关卡 " + levelPath + " 已加载，等待输入");
    }

    private String[] resetLevel() {
        clearLevelState();
        RowBuffer.append("\n[Info] 关卡已重置");
        // 退出关卡回到选关界面：刷新一下当前层级的可见性（存档可能在通关时变化）
        levelTree.enter(levelTree.getCurrentPath(), progress);
        return ResponseBuilder.ack("已退出关卡");
    }

    private void clearLevelState() {
        inLevel = false;
        runtimeContext = null;
        levelConfig = null;
        currentLevelPath = null;
        struct_command_idToCommand = null;
        struct_idToCommands = null;
        structidToStructure = null;
        commandsByTag = null;
        inputBuffer = new StringBuilder();
        selectedOptionIndex = 0;
        currentStep = 0;
    }
 


    // ==========================================
    // 关卡内：处理单次输入 token
    // ==========================================
 
    private String[] handleInput(String action) {
        // exit 相当于原来 run() 循环里的 break
        if ("exit".equalsIgnoreCase(action)) {
            resetLevel();
            return ResponseBuilder.ack("[OK] 已退出关卡");
        }
 
        if ("enter".equalsIgnoreCase(action)) {
            String statement = inputBuffer.toString();
            inputBuffer.setLength(0);
            selectedOptionIndex = 0;
 
            RowBuffer.append("玩家提交语句: " + statement);
            runtimeContext.resetCheckCounts();
            boolean executed = executeStatement(statement, true);
            if (!executed) return new String[0];
            executeJudgeCommands();
            runtimeContext.clearBuffer();
 
            if (runtimeContext.isWinConditionMet()) {
                RowBuffer.append("[过关] 判定条件通过！游戏胜利！");
                // 通关回写：本关 cleared、递归冒泡所有满足 clear_when 的祖先层级
                LevelTree.applyClear(currentLevelPath, progress);
                SaveManager.save(progress);
                // 退回到本关父层级（让玩家看到刚解锁的兄弟节点 / 刚变成 cleared 的标记）
                String parent = LevelTree.parentOf(currentLevelPath);
                clearLevelState();
                levelTree.enter(parent, progress);
                return ResponseBuilder.win();
            }

            currentStep++;
            if (currentStep >= levelConfig.stepsLimit) {
                RowBuffer.append("[结算] 达到最大步数限制，游戏结束。");
                String parent = LevelTree.parentOf(currentLevelPath);
                clearLevelState();
                levelTree.enter(parent, progress);
                return ResponseBuilder.fail("达到最大步数限制，游戏结束");
            }
 
            RowBuffer.append("[循环] 判定未通过，继续循环。剩余步数: " + (levelConfig.stepsLimit - currentStep));
            return ResponseBuilder.levelContinue(currentStep, levelConfig.stepsLimit);
 
        } else if ("tab".equalsIgnoreCase(action)) {
            List<String> options = computeOptions().optionsList;
            if (!options.isEmpty()) {
                int idx = (options.size() == 1) ? 0 : selectedOptionIndex;
                idx = Math.min(idx, options.size() - 1); //emmmm
                inputBuffer.append(options.get(idx));
                selectedOptionIndex = 0;
            }
            return new String[0];
 
        } else if ("del".equalsIgnoreCase(action)) {
            if (inputBuffer.length() > 0) {
                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            }
            selectedOptionIndex = 0;
            return new String[0];
 
        } else if ("up".equalsIgnoreCase(action)) {
            selectedOptionIndex = Math.max(0, selectedOptionIndex - 1);
            return new String[0];
 
        } else if ("down".equalsIgnoreCase(action)) {
            InputAnalysis analysis = computeOptions();
            selectedOptionIndex = Math.min(
                Math.max(0, analysis.optionsList.size() - 1), selectedOptionIndex + 1);
            return new String[0];
 
        } else if (action.length() == 1) {
            inputBuffer.append(action);
            selectedOptionIndex = 0;
            return new String[0];
        }
 
        return ResponseBuilder.error("[Error] 无法识别的 input token: " + action);
    }

    // ==========================================
    // 关卡内：词法分析查询
    // ==========================================
 
    private String[] queryNextCommandPart() {
        InputAnalysis analysis = computeOptions();
        String status = analysis.isExactMatch? "exact"
                : (analysis.isDeadEnd ? "deadend" : "partial");
        return ResponseBuilder.commandHints(
                inputBuffer.toString(),
                status,
                selectedOptionIndex,
                analysis.optionsList);
    }
    
    // ==========================================
    // 词法分析核心（原 interactiveReadCommand 拆出，再次重构）
    // ==========================================
 
     private static class InputAnalysis {
        boolean      isExactMatch = false;
        boolean      isDeadEnd   = true;
        List<String> optionsList  = new ArrayList<>();
    }
    /**
     * 计算当前输入缓冲下的词法补全。
     *
     * 算法：对所有玩家有配额的指令运行 PatternMatcher 推进，收集"下一步可追加片段"——
     *   - 字面量片段：直接加入选项
     *   - 参数 hint 片段：交给 ArgHintResolver.resolveCompletion 按 hint 类型分发
     *     （obj / cmd / any / 将来的扩展），统一返回带括号闭合的补全后缀
     *
     * 该方法只负责"最外层"的推进；cmd[] 内部的递归在 ArgHintResolver 内部完成。
     * 因此最外层的配额过滤、isExactMatch / isDeadEnd 判定都集中在这里。
     */
    private InputAnalysis computeOptions() {
        InputAnalysis result = new InputAnalysis();
        Set<String> uniqueOptions = new LinkedHashSet<>();
        String current = inputBuffer.toString();
 
        for (List<CommandDefinition> defs : struct_idToCommands.values()) {
            for (CommandDefinition def : defs) {
                // 最外层：仅考虑玩家有配额的指令
                if (def.getMaxUses() <= 0 || def.getUsedCount() >= def.getMaxUses()) continue;
 
                // 精确匹配（用于 status=exact 提示）
                if (!result.isExactMatch && PatternMatcher.extractArgs(def, current) != null) {
                    result.isExactMatch = true;
                    result.isDeadEnd    = false;
                }
 
                // 收集该 def 在 current 下的补全后缀（最外层 → checkQuota=true）
                List<String> suffixes = ArgHintResolver.completionsForDef(
                        def, current, true, runtimeContext, struct_idToCommands);
                if (!suffixes.isEmpty()) result.isDeadEnd = false;
                uniqueOptions.addAll(suffixes);
 
                // 即使 suffixes 为空，只要 couldBePrefix 通过，也不算 dead end
                if (result.isDeadEnd && PatternMatcher.couldBePrefix(def, current))
                    result.isDeadEnd = false;
            }
        }
 
        result.optionsList = new ArrayList<>(uniqueOptions);
        return result;
    }
    // ==========================================
    // 原有关卡逻辑，在重构完词法分析后逻辑不变
    // ==========================================

    public void loadLevel(String levelPath) {
        levelConfig = LevelConfigLoader.getConfig(levelPath);
        if (levelConfig.buffer != null) {
            runtimeContext.setBufferConfig(levelConfig.buffer.commandIn, levelConfig.buffer.commandOut);
        }
    }
    public void registerStructures() {// 遍历关卡的结构注册表，加载基础指令
        RowBuffer.append("\n[Debug] === 阶段一: 加载物理结构与挂载基础指令 ===");
        com.algoblock.tools.jsonloader.namerule.StructureRegistry registryConfig = com.algoblock.tools.jsonloader.analysis.StructureRegistryLoader.getRegistry();
        for (String structId : levelConfig.structUsed) {// 
            String fqcn = registryConfig.getFQCN(structId);
            if (fqcn == null) {
                throw new RuntimeException("配置错误：在注册表实例中未找到结构体 [" + structId + "] 的定义");
            }

            try {
                Abstract structInstance = (Abstract) Class.forName(fqcn).getDeclaredConstructor().newInstance(); // 新建结构实例
                structidToStructure.put(structId, structInstance); //结构实例的引用打入结构id-实例引用映射
                struct_idToCommands.put(structId, new ArrayList<>()); //初始化结构id-指令记录表引用映射
                
                // 遍历该实例默认指令记录表，即已加载的方法，用 argHints / tags 构建 CommandDefinition
                for (Map.Entry<String, StructureMethod> entry : structInstance.getLoadedMethods().entrySet()) {
                    CommandDefinition newDef = new CommandDefinition( // 新建指令记录
                            structId,
                            entry.getKey(), //commandId
                            entry.getValue().getPattern(),
                            entry.getValue().getArgHints(),
                            entry.getValue().getTags());
                    newDef.setMaxUses(0); // 默认玩家无使用次数
                    struct_command_idToCommand.put(structId + "_" + entry.getKey(), newDef); // 将指令记录的引用打入双id-指令记录引用映射
                    struct_idToCommands.get(structId).add(newDef); // 将指令记录的引用加入id-指令记录表引用中
                    RowBuffer.append("[Debug] -> [" + structId + "] 成功挂载预设指令: " + entry.getKey() + " | Pattern: "
                            + entry.getValue().getPattern());
                }
            } catch (Exception e) {
                RowBuffer.append("[Debug] [异常] 结构体注册严重失败: " + fqcn);
            }
        }
    }
    public void initAllowedLimits() {
        RowBuffer.append("\n[Debug] === 阶段二: 遍历授权名单更新权限 / 动态拉取新指令 ===");
        for (LevelConfig.CommandConfig commandAllowed : levelConfig.commandsAllowed) {
            String struct_command_id = commandAllowed.structId + "_" + commandAllowed.commandId;
            RowBuffer.append("[Debug] 处理授权清单: 结构[" + commandAllowed.structId + "] - 指令[" + commandAllowed.commandId + "] -> 授权配额: " + commandAllowed.maxUses);
            CommandDefinition oldDef = struct_command_idToCommand.get(struct_command_id);
            RowBuffer.append("[Debug]   |- 预设缓存匹配情况: " + (oldDef != null));
            if (oldDef != null) {
                oldDef.setMaxUses(commandAllowed.maxUses);
                RowBuffer.append("[Debug]   |- [完毕] 仅更新配额上限");
            } else {
                RowBuffer.append("[Debug]   |- 触发向下层架构反射请求");
                Abstract structTemplate = structidToStructure.get(commandAllowed.structId);
                if (structTemplate != null) {
                    boolean loaded = structTemplate.ifLoadMethodDynamically(commandAllowed.commandId); //是否成功动态加载
                    RowBuffer.append("[Debug]   |- 底层架构反馈加载结果: " + loaded);
                    if (loaded) {
                        // 从动态加载后的 StructureMethod 实例拿完整的 pattern / argHints / tags
                        StructureMethod sm = structTemplate.getLoadedMethods().get(commandAllowed.commandId);
                        CommandDefinition newDef = new CommandDefinition(
                                commandAllowed.structId,
                                commandAllowed.commandId,
                                sm.getPattern(),
                                sm.getArgHints(),
                                sm.getTags());
                        newDef.setMaxUses(commandAllowed.maxUses); // 使用次数设为设置值
                        struct_command_idToCommand.put(struct_command_id, newDef); // 将指令记录的引用打入双id-指令记录引用映射
                        if (!struct_idToCommands.containsKey(commandAllowed.structId)) { //不包含当前指令记录的结构id，则需要先初始化防崩
                            struct_idToCommands.put(commandAllowed.structId, new ArrayList<>());
                        }
                        struct_idToCommands.get(commandAllowed.structId).add(newDef); // 将指令记录的引用加入id-指令记录表引用中
                        RowBuffer.append("[Debug]   |- [完毕] 指令已动态组装加入引擎库. Pattern: " + sm.getPattern());
                    }
                }
            }
        }
    }

    /** 在 initAllowedLimits 之后调用，建立 tag → CommandDefinition 列表的索引。 */
    private void buildCommandsByTag() {
        commandsByTag = new HashMap<>();
        for (List<CommandDefinition> defs : struct_idToCommands.values()) {
            for (CommandDefinition def : defs) {
                if (def.getMaxUses() <= 0) continue; // 无权限的不加入索引
                for (String tag : def.getTags()) {
                    commandsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(def);
                }
            }
        }
        RowBuffer.append("[Debug] commandsByTag 构建完成，标签数: " + commandsByTag.size());
    }
 
    public void executeInitCommands() {
        RowBuffer.append("\n--- 初始化阶段 ---");
        for (String initCommand : levelConfig.initCommands) { // 遍历初始化指令逐一执行
            executeStatement(initCommand, false);
        }
    }
    public void executeJudgeCommands() {
        RowBuffer.append("\n--- 判分阶段 ---");
        for (String judge : levelConfig.judgeCommands) { // 遍历判分指令逐一执行
            executeStatement(judge, false);
        }
    }
    /**
     * [致命 Bug 修订]: 全字符穷举判定
     * 解决了截断匹配导致 Queue(A).pop 误命中了 Queue(@) 的问题。
     */
    // ==========================================
    // Update: 语句执行，但是改用 PatternMatcher
    // ==========================================
        private String extractStructId(String statement) {
        int p = statement.indexOf('[');
        int d = statement.indexOf('.');
        if (p != -1 && d != -1) return statement.substring(0, Math.min(p, d));
        if (p != -1) return statement.substring(0, p);
        if (d != -1) return statement.substring(0, d);
        return statement;
    }    public boolean executeStatement(String statement, boolean isPlayerAction) {
        RowBuffer.append("[Debug] >>> 路由语句: " + statement
                + " | 主体: " + (isPlayerAction ? "玩家" : "系统"));
        String structId = extractStructId(statement);
        RowBuffer.append("[Debug] -> 定位结构体: " + structId);
 
        List<CommandDefinition> defs = struct_idToCommands.get(structId);
        if (defs == null) {
            RowBuffer.append("[Debug] -> [拦截] 未找到结构体 [" + structId + "] 的指令库");
            return false;
        }
 
        for (CommandDefinition def : defs) {
            if (isPlayerAction) {
                if (def.getMaxUses() <= 0 || def.getUsedCount() >= def.getMaxUses()) continue;
            }
            String[] args = PatternMatcher.extractArgs(def, statement);
            if (args != null) {
                // cmd[] 语义校验：验证参数内容是否真的匹配约束的指令格式
                if (!validateCmdArgs(def, args)) continue;
 
                RowBuffer.append("[Debug] -> [匹配] " + def.getCommandId()
                        + " args=" + Arrays.toString(args));
                if (isPlayerAction) {
                    def.incrementUsedCount();
                    RowBuffer.append("[Debug]    |- 已用: "
                            + def.getUsedCount() + "/" + def.getMaxUses());
                }
                runtimeContext.setIsPlayerAction(isPlayerAction);
                structidToStructure.get(def.getStructId())
                        .executeCommand(def.getCommandId(), args, runtimeContext);
                runtimeContext.setIsPlayerAction(false);
                return true;
            }
        }
        RowBuffer.append("[Debug] -> [失败] 无匹配 pattern");
        return false;
    }
 
    /**
     * 对提取出的 args 做 cmd[] 语义验证。
     * 遍历每个参数，若对应的 argHint 是 cmd[] 类型，
     * 则验证该参数字符串能匹配至少一条筛选出的 CommandDefinition。
     */
    /**
     * 对提取出的 args 逐个做语义校验。
     * 每个参数走 ArgHintResolver.validateArg —— 内部按 hint 类型分发到对应 spec。
     * any 永远通过、obj 检查存活、cmd 检查能匹配某条 def。
     */
    private boolean validateCmdArgs(CommandDefinition def, String[] args) {
        String[] argHints = def.getArgHints();
        for (int i = 0; i < args.length; i++) {
            String hint = (i < argHints.length) ? argHints[i] : "any";
            if (!ArgHintResolver.validateArg(hint, args[i], runtimeContext, struct_idToCommands)) {
                RowBuffer.append("[Debug]    |- [语义拒绝] 参数 " + i + " \"" + args[i]
                        + "\" 不符合约束: " + hint);
                return false;
            }
        }
        return true;
    }

    public void triggerEngineCommand(String statement) {
        RowBuffer.append("[Debug] *** 引擎触发后台隐式命令：" + statement);
        executeStatement(statement, false);
    }
}