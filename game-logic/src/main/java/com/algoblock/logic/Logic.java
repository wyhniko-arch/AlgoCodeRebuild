package com.algoblock.logic;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.tools.jsonloader.analysis.LevelConfigLoader;
import com.algoblock.tools.jsonloader.namerule.LevelConfig;
import com.google.gson.JsonObject;
import com.algoblock.tools.buffer.RowBuffer;

import java.util.*;


public class Logic {
    // ==========================================
    // 单例基础设施
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
    // ==========================================
    // 关卡级生命周期字段（start 时初始化，reset 时清空）
    // ==========================================
    private RuntimeContext runtimeContext = null;
    private LevelConfig levelConfig = null;
    private Map<String, Abstract> structidToStructure = null;
    private Map<String, CommandDefinition> struct_command_idToCommand = null;    // 搜索优化，存储引用
    private Map<String, List<CommandDefinition>> struct_idToCommands = null;    // 搜索优化，存储引用

    // 关卡内交互状态
    private StringBuilder inputBuffer = new StringBuilder();
    private int selectedOptionIndex = 0;
    private int currentStep = 0;
    private boolean inLevel = false;
 
    private Logic() {}

    // ==========================================
    // 唯一对外公开的方法
    // ==========================================
 
    /**
     * 状态机统一入口。所有响应均为单元素 String[]，内容是 JSON 字符串。
     *
     * 指令格式：
     *   query:rowbuffer              — 返回 RowBuffer 全部调试内容（全局可用）
     *   query:rowbuffer:N            — 返回最近 N 条调试内容（全局可用）
     *   action:start:<levelIndex>    — 进入关卡（执行 loadLevel/register/init）
     *   action:reset                 — 退出当前关卡，清空关卡状态
     *   action:input:<token>         — 关卡内输入（字符 / tab / del / enter / up / down / exit）
     *   query:nextcommandpart        — 关卡内词法分析，返回当前补全候选项，包含 selectedIndex
     *   query:objects                — 关卡内所有活着游戏对象的 JSON 快照
     *   query:levelinfo              — 关卡全局信息（剩余步数、buffer 指令等，随着更新扩充）
     *
     * 返回值：字符串数组，空数组表示"执行了但无输出"。
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
        if (command == null || command.isBlank()) {
            return new String[0];
        }
 
        // --- query:rowbuffer ---
        if (command.equalsIgnoreCase("query:rowbuffer")) {
            List<String> rows = RowBuffer.getRecent();
            return rows.toArray(new String[0]);
        }
        if (command.toLowerCase().startsWith("query:rowbuffer:")) {
            String tail = command.substring("query:rowbuffer:".length()).trim();
            try {
                int k = Integer.parseInt(tail);
                List<String> rows = RowBuffer.getRecent(k);
                return rows.toArray(new String[0]);
            } catch (NumberFormatException e) {
                return new String[]{"[Error] query:rowbuffer:<N> 中 N 必须是整数"};
            }
        }
 
        // --- action:start:<levelIndex> ---
        if (command.toLowerCase().startsWith("action:start:")) {
            String tail = command.substring("action:start:".length()).trim();
            try {
                int levelIndex = Integer.parseInt(tail);
                return startLevel(levelIndex);
            } catch (NumberFormatException e) {
                return new String[]{"[Error] action:start:<levelIndex> 中关卡号必须是整数"};
            }
        }
 
        // --- action:reset ---
        if (command.equalsIgnoreCase("action:reset")) {
            return resetLevel();
        }
 
        // --- 以下指令只在关卡内有效 ---
        if (!inLevel) {
            return new String[]{"[Info] 当前未在关卡中，请先执行 action:start:<levelIndex>"};
        }
 
        // --- action:input:<token> ---
        if (command.toLowerCase().startsWith("action:input:")) {
            String token = command.substring("action:input:".length());
            return handleInput(token);
        }
 
        // --- query:nextcommandpart ---
        if (command.equalsIgnoreCase("query:nextcommandpart")) {
            return queryNextCommandPart();
        }

        // --- query:objects ---
        if (command.equalsIgnoreCase("query:objects")) {
            List<JsonObject> snapshots = runtimeContext.collectAllSnapshots(structidToStructure);
            return ResponseBuilder.objects(snapshots);
        }

        // --- query:levelinfo ---
        if (command.equalsIgnoreCase("query:levelinfo")) {
            return ResponseBuilder.levelInfo(
                    currentStep,
                    levelConfig.stepsLimit,
                    runtimeContext.getBufferCommandIn(),
                    runtimeContext.getBufferCommandOut(),
                    levelConfig.inputDesc,
                    levelConfig.outputDesc);
        }

        return new String[]{"[Error] 未知指令: " + command};
    }
    

    
    // ==========================================
    // 关卡生命周期
    // ==========================================
 
    private String[] startLevel(int levelIndex) {
        clearLevelState();  // 清空上一关卡状态

        // 初始化关卡级字段
        runtimeContext = new RuntimeContext(this);
        struct_command_idToCommand = new HashMap<>();
        struct_idToCommands = new HashMap<>();
        structidToStructure = new HashMap<>();
        inputBuffer = new StringBuilder();
        selectedOptionIndex = 0;
        currentStep = 0;
        inLevel = true;
 
        loadLevel(levelIndex);
        registerStructures();
        initAllowedLimits();
        executeInitCommands();
 
        RowBuffer.append("\n--- 进入关卡主循环 (关卡 " + levelIndex + ") ---");
        return new String[]{"[OK] 关卡 " + levelIndex + " 已加载，等待输入"};
    }
 
    private String[] resetLevel() {
        clearLevelState();
        RowBuffer.append("\n[Info] 关卡已重置");
        return new String[]{"[OK] 已退出关卡"};
    }
 
    private void clearLevelState() {
        inLevel = false;
        runtimeContext = null;
        levelConfig = null;
        struct_command_idToCommand = null;
        struct_idToCommands = null;
        structidToStructure = null;
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
            return new String[]{"[OK] 已退出关卡"};
        }
 
        if ("enter".equalsIgnoreCase(action)) {
            String statement = inputBuffer.toString();
            inputBuffer.setLength(0);
            selectedOptionIndex = 0;
 
            RowBuffer.append("玩家提交语句: " + statement);
            runtimeContext.resetCheckCounts();
            boolean executed = executeStatement(statement, true);
            if (!executed) {
                return new String[0];
            }
            executeJudgeCommands();
            runtimeContext.clearBuffer();
 
            if (runtimeContext.isWinConditionMet()) {
                RowBuffer.append("[过关] 判定条件通过！游戏胜利！");
                inLevel = false;
                return new String[]{"[WIN] 过关！"};
            }
 
            currentStep++;
            if (currentStep >= levelConfig.stepsLimit) {
                RowBuffer.append("[结算] 达到最大步数限制，游戏结束。");
                inLevel = false;
                return new String[]{"[FAIL] 达到最大步数限制，游戏结束"};
            }
 
            RowBuffer.append("[循环] 判定未通过，继续循环。剩余步数: " + (levelConfig.stepsLimit - currentStep));
            return new String[0];
 
        } else if ("tab".equalsIgnoreCase(action)) {
            List<String> options = computeOptions().optionsList;
            if (!options.isEmpty()) {
                int idx = (options.size() == 1) ? 0 : selectedOptionIndex;
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
            List<String> options = computeOptions().optionsList;
            selectedOptionIndex = Math.max(0, selectedOptionIndex - 1);
            return new String[0];
 
        } else if ("down".equalsIgnoreCase(action)) {
            List<String> options = computeOptions().optionsList;
            selectedOptionIndex = Math.min(options.size() - 1, selectedOptionIndex + 1);
            return new String[0];
 
        } else if (action.length() == 1) {
            inputBuffer.append(action);
            selectedOptionIndex = 0;
            return new String[0];
        }
 
        return new String[]{"[Error] 无法识别的 input token: " + action};
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
    // 词法分析核心（原 interactiveReadCommand 拆出）
    // ==========================================
 
    private static class InputAnalysis {
        boolean isExactMatch;
        boolean isDeadEnd;
        List<String> optionsList;
    }
 
    private InputAnalysis computeOptions() {
        InputAnalysis result = new InputAnalysis();
        result.isExactMatch = false;
        result.isDeadEnd = true;
        Set<String> uniqueOptions = new LinkedHashSet<>();
        String current = inputBuffer.toString();
 
        for (Map.Entry<String, List<CommandDefinition>> entry : struct_idToCommands.entrySet()) {
            String structId = entry.getKey();
            Set<String> activeVars = runtimeContext.getActiveObjectNames(structId);
 
            for (CommandDefinition def : entry.getValue()) {
                if (def.getMaxUses() > 0 && def.getUsedCount() < def.getMaxUses()) {
 
                    if (!result.isExactMatch && extractArgumentsFast(def, current) != null) {
                        result.isExactMatch = true;
                        result.isDeadEnd = false;
                    }
 
                    if (result.isDeadEnd && couldBePrefix(def.getPattern(), current)) {
                        result.isDeadEnd = false;
                    }
 
                    List<List<String>> tokenSequences = new ArrayList<>();
                    generateTokensRecursive(def.getLiterals(), 0, new ArrayList<>(), activeVars, tokenSequences);
                    for (List<String> seq : tokenSequences) {
                        String fullStr = String.join("", seq);
                        if (fullStr.startsWith(current) && !fullStr.equals(current)) {
                            int currentLen = 0;
                            for (String token : seq) {
                                int start = currentLen;
                                int end = currentLen + token.length();
                                if (current.length() >= start && current.length() < end) {
                                    uniqueOptions.add(token.substring(current.length() - start));
                                    break;
                                }
                                currentLen += token.length();
                            }
                        }
                    }
                }
            }
        }
 
        result.optionsList = new ArrayList<>(uniqueOptions);
        return result;
    }
    // ==========================================
    // 原有关卡逻辑
    // ==========================================

    public void loadLevel(int levelIndex) {// 从外部加载器获取已解析好JSON数据的关卡实例
        levelConfig = LevelConfigLoader.getConfig(levelIndex);
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
                for (Map.Entry<String, String> entry : structInstance.getPatterns().entrySet()) { // 遍历该实例默认指令记录表
                    CommandDefinition newDef = new CommandDefinition(structId, entry.getKey(), entry.getValue()); // 新建指令记录
                    newDef.setMaxUses(0); // 默认玩家无使用次数
                    struct_command_idToCommand.put(structId + "_" + entry.getKey(), newDef); // 将指令记录的引用打入双id-指令记录引用映射
                    struct_idToCommands.get(structId).add(newDef); // 将指令记录的引用加入id-指令记录表引用中
                    RowBuffer.append("[Debug] -> [" + structId + "] 成功挂载预设指令: " + entry.getKey() + " | Pattern: "
                            + entry.getValue());
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
                        String pattern = structTemplate.getPatterns().get(commandAllowed.commandId);
                        CommandDefinition newDef = new CommandDefinition(commandAllowed.structId, commandAllowed.commandId, pattern); // 新建指令记录
                        newDef.setMaxUses(commandAllowed.maxUses); // 使用次数设为设置值
                        struct_command_idToCommand.put(struct_command_id, newDef); // 将指令记录的引用打入双id-指令记录引用映射
                        if (!struct_idToCommands.containsKey(commandAllowed.structId)) { //不包含当前指令记录的结构id，则需要先初始化防崩
                            struct_idToCommands.put(commandAllowed.structId, new ArrayList<>());
                        }
                        struct_idToCommands.get(commandAllowed.structId).add(newDef); // 将指令记录的引用加入id-指令记录表引用中
                        RowBuffer.append("[Debug]   |- [完毕] 指令已动态组装加入引擎库. Pattern: " + pattern);
                    }
                }
            }
        }
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
    private String[] extractArgumentsFast(CommandDefinition def, String statement) {
        String[] literals = def.getLiterals();
        List<String> args = new ArrayList<>(literals.length - 1);
        int cursor = 0;

        for (int i = 0; i < literals.length; i++) {
            String lit = literals[i];
            if (i == 0) {
                if (!statement.startsWith(lit))
                    return null;
                cursor = lit.length();
            } else {
                if (lit.isEmpty()) {
                    // 处理末尾直接是 @ 的情况 (例如 Pattern = "@")
                    args.add(statement.substring(cursor));
                    cursor = statement.length();
                } else {
                    int matchIdx = statement.indexOf(lit, cursor);
                    if (matchIdx == -1)
                        return null;
                    args.add(statement.substring(cursor, matchIdx));
                    cursor = matchIdx + lit.length();
                }
            }
        }
        // 【核心校验】：确保模式在游标走完时，语句也严丝合缝地被穷尽
        if (cursor != statement.length()) return null;
        return args.toArray(new String[0]);
    }
    private String extractStructId(String statement) {
        int firstParen = statement.indexOf('(');
        int firstDot = statement.indexOf('.');
        if (firstParen != -1 && firstDot != -1)
            return statement.substring(0, Math.min(firstParen, firstDot));
        else if (firstParen != -1)
            return statement.substring(0, firstParen);
        else if (firstDot != -1)
            return statement.substring(0, firstDot);
        return statement;
    }
    public boolean executeStatement(String statement, boolean isPlayerAction) {
        RowBuffer.append("[Debug] >>> 引擎开始路由分析语句: " + statement + " | 执行主体: " + (isPlayerAction ? "玩家" : "系统"));
        String structId = extractStructId(statement);
        RowBuffer.append("[Debug] -> O(1) 预检定位到的主结构体 ID 为: " + structId);
        List<CommandDefinition> defs = struct_idToCommands.get(structId);
        if (defs == null) {
            RowBuffer.append("[Debug] -> [拦截] 未找到结构体 [" + structId + "] 的相关指令库");
            return false;
        }
        for (CommandDefinition def : defs) {
            // [玩家权限校验 Bug 修复]：执行者是玩家时，若配额不足或为零（系统指令），直接将此模式剔除出匹配范围
            if (isPlayerAction) {
                if (def.getMaxUses() <= 0)
                    continue;
                if (def.getUsedCount() >= def.getMaxUses())
                    continue;
            }
            String[] args = extractArgumentsFast(def, statement);
            if (args != null) {
                RowBuffer.append("[Debug] -> [匹配成功] 成功锁定指令!");
                RowBuffer.append("[Debug]    |- 结构 ID: " + def.getStructId());
                RowBuffer.append("[Debug]    |- 指令 ID: " + def.getCommandId());
                RowBuffer.append("[Debug]    |- Pattern: " + def.getPattern());
                RowBuffer.append("[Debug]    |- 提取参数: " + Arrays.toString(args));

                if (isPlayerAction) {
                    def.incrementUsedCount();
                    RowBuffer.append("[Debug]    |- 玩家已用次数更新: " + def.getUsedCount() + " / " + def.getMaxUses());
                }

                Abstract template = structidToStructure.get(def.getStructId());
                RowBuffer.append("[Debug] -> 即将向子结构 [" + def.getStructId() + "] 抛出 executeCommand 调度");
                runtimeContext.setIsPlayerAction(isPlayerAction); // 设置当前执行上下文的玩家指令标志
                template.executeCommand(def.getCommandId(), args, runtimeContext); // 结构端分发器：引擎将提取好的参数传递给结构
                runtimeContext.setIsPlayerAction(false); // 重置玩家指令标志，防止连锁误触
                return true;
            }
        }

        RowBuffer.append("[Debug] -> [匹配失败] 该语句与所有合法的模式均不匹配");
        return false;
    }

    public void triggerEngineCommand(String statement) {
        RowBuffer.append("[Debug] *** 引擎触发后台隐式命令 ***");
        executeStatement(statement, false);
    }

    // ==========================================
    // 玩家词法交互与渲染引擎模块
    // ==========================================

    private void generateTokensRecursive(String[] literals, int varIdx, List<String> currentTokens,
            Set<String> activeVars, List<List<String>> result) {
        List<String> nextTokens = new ArrayList<>(currentTokens);
        if (!literals[varIdx].isEmpty())
            nextTokens.add(literals[varIdx]);

        if (varIdx == literals.length - 1) {
            result.add(nextTokens);
            return;
        }
        for (String var : activeVars) {
            List<String> pathWithVar = new ArrayList<>(nextTokens);
            pathWithVar.add(var);
            generateTokensRecursive(literals, varIdx + 1, pathWithVar, activeVars, result);
        }
    }

    private boolean couldBePrefix(String pattern, String input) {
        String[] literals = pattern.split("@", -1);
        int inputCursor = 0;
        for (int i = 0; i < literals.length; i++) {
            String lit = literals[i];
            if (inputCursor >= input.length()) return true;

            if (i == 0) {
                if (input.length() < lit.length()) return lit.startsWith(input);
                if (!input.startsWith(lit)) return false;
                inputCursor += lit.length();
            } else {
                int matchIdx = input.indexOf(lit, inputCursor);
                if (matchIdx == -1) return true; // 未遇到结尾闭合点，宽容放行
                inputCursor = matchIdx + lit.length();
            }
        }
        return true;
    }
    /*
    private String interactiveReadCommand(Scanner scanner) {
        StringBuilder buffer = new StringBuilder();
        int selectedOptionIndex = 0;

        while (true) {
            boolean isExactMatch = false;
            boolean isDeadEnd = true;
            Set<String> uniqueOptions = new LinkedHashSet<>();

            for (Map.Entry<String, List<CommandDefinition>> entry : struct_idToCommands.entrySet()) {
                String structId = entry.getKey();
                // [结构命名空间隔离]：此时 @ 严格与该特定结构的对象群绑定
                Set<String> activeVars = runtimeContext.getActiveObjectNames(structId);

                for (CommandDefinition def : entry.getValue()) {
                    // 预测范围过滤：玩家无法使用的指令不得提供 UI 补全联想
                    if (def.getMaxUses() > 0 && def.getUsedCount() < def.getMaxUses()) {

                        if (!isExactMatch && extractArgumentsFast(def, buffer.toString()) != null) {
                            isExactMatch = true;
                            isDeadEnd = false;
                        }

                        if (isDeadEnd && couldBePrefix(def.getPattern(), buffer.toString())) {
                            isDeadEnd = false;
                        }

                        List<List<String>> tokenSequences = new ArrayList<>();
                        generateTokensRecursive(def.getLiterals(), 0, new ArrayList<>(), activeVars, tokenSequences);
                        for (List<String> seq : tokenSequences) {
                            String fullStr = String.join("", seq);
                            if (fullStr.startsWith(buffer.toString()) && !fullStr.equals(buffer.toString())) {
                                int currentLen = 0;
                                for (String token : seq) {
                                    int start = currentLen;
                                    int end = currentLen + token.length();
                                    if (buffer.length() >= start && buffer.length() < end) {
                                        uniqueOptions.add(token.substring(buffer.length() - start));
                                        break;
                                    }
                                    currentLen += token.length();
                                }
                            }
                        }
                    }
                }
            }

            List<String> optionsList = new ArrayList<>(uniqueOptions);
            String inputColor = isExactMatch ? TerminalUtils.GREEN
                    : (isDeadEnd ? TerminalUtils.RED : TerminalUtils.WHITE);

            TerminalUtils.clearCurrentLine();
            System.out.print("\r\033[0J");
            System.out.print("> " + inputColor + buffer.toString() + TerminalUtils.RESET);

            if (!isExactMatch && !optionsList.isEmpty()) {
                if (optionsList.size() == 1) {
                    System.out.print(TerminalUtils.GOLD + optionsList.get(0) + TerminalUtils.RESET);
                } else {
                    System.out.println();
                    for (int i = 0; i < optionsList.size(); i++) {
                        if (i == selectedOptionIndex) {
                            System.out
                                    .println(TerminalUtils.GOLD + "[" + optionsList.get(i) + "]" + TerminalUtils.RESET);
                        } else {
                            System.out.println(TerminalUtils.GRAY + optionsList.get(i) + TerminalUtils.RESET);
                        }
                    }
                    TerminalUtils.clearBelowAndRenderUp(optionsList.size(), 2 + buffer.length());
                }
            }

            String action = scanner.nextLine().trim();

            if ("enter".equalsIgnoreCase(action)) {
                System.out.println();
                return buffer.toString();
            } else if ("tab".equalsIgnoreCase(action)) {
                if (!optionsList.isEmpty()) {
                    int idx = (optionsList.size() == 1) ? 0 : selectedOptionIndex;
                    buffer.append(optionsList.get(idx));
                    selectedOptionIndex = 0;
                }
            } else if ("del".equalsIgnoreCase(action)) {
                if (buffer.length() > 0)
                    buffer.deleteCharAt(buffer.length() - 1);
                selectedOptionIndex = 0;
            } else if ("up".equalsIgnoreCase(action)) {
                selectedOptionIndex = Math.max(0, selectedOptionIndex - 1);
            } else if ("down".equalsIgnoreCase(action)) {
                selectedOptionIndex = Math.min(optionsList.size() - 1, selectedOptionIndex + 1);
            } else if (action.length() == 1) {
                buffer.append(action);
                selectedOptionIndex = 0;
            }
        }
    }*/
    /*
    public void run(int levelIndex) {
        loadLevel(levelIndex);
        registerStructures();
        initAllowedLimits();
        executeInitCommands();
        RowBuffer.append("\n--- 进入关卡主循环 ---");
        Scanner scanner = new Scanner(System.in);
        int currentStep = 0;
        while (currentStep < levelConfig.stepsLimit) {
            runtimeContext.resetCheckCounts();
            RowBuffer.append("等待一个语句输入 (请输入单字符，或控制命令 [tab, del, enter, up, down]):");
            String input = interactiveReadCommand(scanner);
            if ("exit".equalsIgnoreCase(input.trim())) break;
            RowBuffer.append("检查该语句是否可执行");
            boolean executed = executeStatement(input, true);
        if (!executed) continue;
            executeJudgeCommands();
            runtimeContext.clearBuffer();
            if (runtimeContext.isWinConditionMet()) {
                RowBuffer.append("[过关] 判定条件通过！游戏胜利！");
                break;
            } else {
                RowBuffer.append("[循环] 判定未通过，继续循环。剩余步数: " + (levelConfig.stepsLimit - currentStep - 1));
            }
            currentStep++;
        }
        if (currentStep >= levelConfig.stepsLimit && !runtimeContext.isWinConditionMet()) {
            RowBuffer.append("[结算] 达到最大步数限制，游戏结束。");
        }
        scanner.close();
    } */
}