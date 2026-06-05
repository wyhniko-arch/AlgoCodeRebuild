package com.algoblock.core;
import com.algoblock.config.LevelConfig;
import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.util.LevelConfigLoader;

import java.util.*;

class TerminalUtils {
    public static final String RESET = "\033[0m";
    public static final String RED = "\033[31m";
    public static final String GREEN = "\033[32m";
    public static final String WHITE = "\033[37m";
    public static final String GRAY = "\033[90m";
    public static final String GOLD = "\033[33m";

    public static void clearCurrentLine() {
        System.out.print("\033[2K\r");
    }

    public static void clearBelowAndRenderUp(int linesToMoveUp, int colsToMoveRight) {
        System.out.print("\033[" + linesToMoveUp + "A");
        System.out.print("\r\033[" + colsToMoveRight + "C");
    }
}

public class Core {
    private final RuntimeContext runtimeContext = new RuntimeContext(this);

    private final Map<String, InstructionDefinition> identityToInstruction = new HashMap<>();
    private final Map<String, List<InstructionDefinition>> structToInstructions = new HashMap<>();
    private final Map<String, Abstract> structureTemplates = new HashMap<>();

    private List<String> structUsed;
    private Map<String, Integer> instsAllowed;
    private List<String> initInsts;
    private List<String> judgeInsts;
    private int stepsLimit;

    public void loadLevel(int levelIndex) {
        // 1. 插入行：从外部加载器获取已解析好 JSON 数据的实例
        LevelConfig config = LevelConfigLoader.getConfig(levelIndex);
        
        structUsed = config.structUsed;
        instsAllowed = new HashMap<>();
        if (config.instsAllowed != null) {
            for (LevelConfig.InstConfig instConfig : config.instsAllowed) {
                instsAllowed.put(instConfig.struct + "_" + instConfig.instId, instConfig.maxUses);
            }
        }
        initInsts = config.initInsts;
        judgeInsts = config.judgeInsts;
        if (config.buffer != null) {
            runtimeContext.setBufferConfig(config.buffer.instIn, config.buffer.instOut);
        }
        stepsLimit = config.stepsLimit;
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public void registerStructures() {
        System.out.println("\n[Debug] === 阶段一: 加载物理结构与挂载基础指令 ===");
        com.algoblock.config.StructureRegistry  
            registryConfig = com.algoblock.util.StructureRegistryLoader.getRegistry();
        Map<String, String> registry = new HashMap<>();
        //registry.put("Queue", "com.algoblock.structure.queue.FakeQueue");
        //registry.put("Stack", "com.algoblock.structure.stack.FakeStack");
        // 3. 遍历当前关卡启用的结构体
        for (String struct : structUsed) {
            // 4. 从实例里找到对应的相对路径字符串 (如 "queue/FakeQueue.java")
            String relativePath = registryConfig.getPath(struct);
            
            if (relativePath == null) {
                throw new RuntimeException("配置错误：在注册表实例中未找到结构体 [" + struct + "] 的定义");
            }
            // 5. 按照大环境规范进行字符串拼接转换
            String dotNotation = relativePath.replace('/', '.');
            if (dotNotation.endsWith(".java")) {
                dotNotation = dotNotation.substring(0, dotNotation.length() - 5);
            }
            String fqcn = "com.algoblock.structure." + dotNotation;
            // 6. 愉快地一个个存入本地 registry 映射表：(结构, 路径)
            registry.put(struct, fqcn);
        }
        
        for (String struct : structUsed) {
            String fqcn = registry.get(struct);
            try {
                Abstract structInstance = (Abstract) Class.forName(fqcn).getDeclaredConstructor().newInstance();
                structureTemplates.put(struct, structInstance);
                structToInstructions.put(struct, new ArrayList<>());

                Map<String, String> patternsMap = structInstance.getPatterns();
                for (Map.Entry<String, String> entry : patternsMap.entrySet()) {
                    InstructionDefinition def = new InstructionDefinition(struct, entry.getKey(), entry.getValue());
                    def.setMaxUses(0); // 初始化设为 0
                    identityToInstruction.put(struct + "_" + entry.getKey(), def);
                    structToInstructions.get(struct).add(def);
                    System.out.println("[Debug] -> [" + struct + "] 成功挂载预设指令: " + entry.getKey() + " | Pattern: "
                            + entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("[Debug] [异常] 结构体注册严重失败: " + fqcn);
            }
        }
    }

    public void initAllowedLimits() {
        System.out.println("\n[Debug] === 阶段二: 遍历授权名单更新权限 / 动态拉取新指令 ===");
        for (Map.Entry<String, Integer> entry : instsAllowed.entrySet()) {
            String identityKey = entry.getKey();
            String[] parts = identityKey.split("_", 2);
            if (parts.length < 2)
                continue;
            String structId = parts[0];
            String instId = parts[1];

            System.out
                    .println("[Debug] 处理授权清单: 结构[" + structId + "] - 指令[" + instId + "] -> 授权配额: " + entry.getValue());

            InstructionDefinition existingDef = identityToInstruction.get(identityKey);
            System.out.println("[Debug]   |- 预设缓存匹配情况: " + (existingDef != null));

            if (existingDef != null) {
                existingDef.setMaxUses(entry.getValue());
                System.out.println("[Debug]   |- [完毕] 仅更新配额上限");
            } else {
                System.out.println("[Debug]   |- 触发向下层架构反射请求");
                Abstract structTemplate = structureTemplates.get(structId);
                if (structTemplate != null) {
                    boolean loaded = structTemplate.loadMethodDynamically(instId);
                    System.out.println("[Debug]   |- 底层架构反馈加载结果: " + loaded);
                    if (loaded) {
                        String pattern = structTemplate.getPatterns().get(instId);
                        InstructionDefinition newDef = new InstructionDefinition(structId, instId, pattern);
                        newDef.setMaxUses(entry.getValue());

                        identityToInstruction.put(identityKey, newDef);
                        if (!structToInstructions.containsKey(structId)) {
                            structToInstructions.put(structId, new ArrayList<>());
                        }
                        structToInstructions.get(structId).add(newDef);
                        System.out.println("[Debug]   |- [完毕] 指令已动态组装加入引擎库. Pattern: " + pattern);
                    }
                }
            }
        }
    }

    /**
     * [致命 Bug 修订]: 全字符穷举判定
     * 解决了截断匹配导致 Queue(A).pop 误命中了 Queue(@) 的问题。
     */
    private String[] extractArgumentsFast(InstructionDefinition def, String statement) {
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
        if (cursor != statement.length())
            return null;

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
        System.out.println("[Debug] >>> 引擎开始路由分析语句: " + statement + " | 执行主体: " + (isPlayerAction ? "玩家" : "系统"));

        String structId = extractStructId(statement);
        System.out.println("[Debug] -> O(1) 预检定位到的主结构体 ID 为: " + structId);

        List<InstructionDefinition> defs = structToInstructions.get(structId);
        if (defs == null) {
            System.out.println("[Debug] -> [拦截] 未找到结构体 [" + structId + "] 的相关指令库");
            return false;
        }

        for (InstructionDefinition def : defs) {
            // [玩家权限校验 Bug 修复]：执行者是玩家时，若配额不足或为零（系统指令），直接将此模式剔除出匹配范围
            if (isPlayerAction) {
                if (def.getMaxUses() <= 0)
                    continue;
                if (def.getUsedCount() >= def.getMaxUses())
                    continue;
            }

            String[] args = extractArgumentsFast(def, statement);
            if (args != null) {
                System.out.println("[Debug] -> [匹配成功] 成功锁定指令!");
                System.out.println("[Debug]    |- 结构 ID: " + def.getStructId());
                System.out.println("[Debug]    |- 指令 ID: " + def.getInstId());
                System.out.println("[Debug]    |- Pattern: " + def.getPattern());
                System.out.println("[Debug]    |- 提取参数: " + Arrays.toString(args));

                if (isPlayerAction) {
                    def.incrementUsedCount();
                    System.out.println("[Debug]    |- 玩家已用次数更新: " + def.getUsedCount() + " / " + def.getMaxUses());
                }

                Abstract template = structureTemplates.get(def.getStructId());
                System.out.println("[Debug] -> 即将向子结构 [" + def.getStructId() + "] 抛出 executeInstruction 调度");
                runtimeContext.setIsPlayerAction(isPlayerAction); // 设置当前执行上下文的玩家指令标志
                template.executeInstruction(def.getInstId(), args, runtimeContext); // 结构端分发器：引擎将提取好的参数传递给结构
                runtimeContext.setIsPlayerAction(false); // 重置玩家指令标志，防止连锁误触
                return true;
            }
        }

        System.out.println("[Debug] -> [匹配失败] 该语句与所有合法的模式均不匹配");
        return false;
    }

    public void triggerEngineCommand(String statement) {
        System.out.println("[Debug] *** 引擎触发后台隐式命令 ***");
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
            if (inputCursor >= input.length())
                return true;

            if (i == 0) {
                if (input.length() < lit.length())
                    return lit.startsWith(input);
                if (!input.startsWith(lit))
                    return false;
                inputCursor += lit.length();
            } else {
                int matchIdx = input.indexOf(lit, inputCursor);
                if (matchIdx == -1)
                    return true; // 未遇到结尾闭合点，宽容放行
                inputCursor = matchIdx + lit.length();
            }
        }
        return true;
    }

    private String interactiveReadCommand(Scanner scanner) {
        StringBuilder buffer = new StringBuilder();
        int selectedOptionIndex = 0;

        while (true) {
            boolean isExactMatch = false;
            boolean isDeadEnd = true;
            Set<String> uniqueOptions = new LinkedHashSet<>();

            for (Map.Entry<String, List<InstructionDefinition>> entry : structToInstructions.entrySet()) {
                String structId = entry.getKey();
                // [结构命名空间隔离]：此时 @ 严格与该特定结构的对象群绑定
                Set<String> activeVars = runtimeContext.getActiveObjectNames(structId);

                for (InstructionDefinition def : entry.getValue()) {
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
    }

    public void run(int levelIndex) {
        loadLevel(levelIndex);
        registerStructures();
        initAllowedLimits();

        System.out.println("\n--- 初始化阶段 ---");
        for (String initInst : initInsts) {
            executeStatement(initInst, false);
        }

        System.out.println("\n--- 进入关卡主循环 ---");
        Scanner scanner = new Scanner(System.in);
        int currentStep = 0;

        while (currentStep < stepsLimit) {
            System.out.println("\n[步骤1] 归零游戏对象栈的两个变量");
            runtimeContext.resetCheckCounts();

            System.out.println("[步骤2] 等待一个语句输入 (请输入单字符，或控制命令 [tab, del, enter, up, down]):");
            String input = interactiveReadCommand(scanner);
            if ("exit".equalsIgnoreCase(input.trim()))
                break;

            System.out.println("[步骤3] 检查该语句是否可执行");
            boolean executed = executeStatement(input, true);
            if (!executed)
                continue;

            System.out.println("[步骤5] 执行完后对judge_insts里的语句顺序逐一执行");
            for (String judge : judgeInsts) {
                executeStatement(judge, false);
            }

            System.out.println("[步骤6] 清空游戏对象栈的缓冲区");
            runtimeContext.clearBuffer();

            System.out.println("[步骤7] 判断是否退出大循环");
            if (runtimeContext.isWinConditionMet()) {
                System.out.println("[过关] 判定条件通过！游戏胜利！");
                break;
            } else {
                System.out.println("[循环] 判定未通过，继续循环。剩余步数: " + (stepsLimit - currentStep - 1));
            }
            currentStep++;
        }

        if (currentStep >= stepsLimit && !runtimeContext.isWinConditionMet()) {
            System.out.println("[结算] 达到最大步数限制，游戏结束。");
        }
        scanner.close();
    }
}