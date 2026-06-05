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

// ==========================================
// 词法分析结果的数据结构体
// ==========================================
enum InstructionStatus {
    ERROR, INCOMPLETE, EXACT_MATCH, MATCHED_WITH_EXTENSIONS
}

class LexicalComponent {
    public final String text;
    public final String encoding; // 由'1'和'2'组成的编码串
    public LexicalComponent(String text, String encoding) {
        this.text = text;
        this.encoding = encoding;
    }
}

class LexicalAnalysisResult {
    public InstructionStatus status;
    public String inputEncoding;
    public List<LexicalComponent> nextOptions = new ArrayList<>();
}

class TypedToken {
    public final String text;
    public final char type; // '1'代表字面量, '2'代表变量名
    public TypedToken(String text, char type) {
        this.text = text;
        this.type = type;
    }
}

// ==========================================
// 引擎核心
// ==========================================
public class Core {

    private final RuntimeContext runtimeContext = new RuntimeContext(this);
    
    // 关卡配置不再分散拷贝，直接持有完整的实例，贯穿生命周期
    private LevelConfig levelConfig;

    // 1. 物理结构体数组：存储所有指令行记录
    private final List<InstructionDefinition> instructionTable = new ArrayList<>();
    // 2. 联合键索引表：联合键 ("Queue_init_full") -> 数组下标
    private final Map<String, Integer> identityToIndex = new HashMap<>();
    // 3. 结构空间索引表：结构ID ("Queue") -> 该结构注册的所有指令的下标数组
    private final Map<String, List<Integer>> structToIndices = new HashMap<>();

    private final Map<String, Abstract> structureTemplates = new HashMap<>();

    public void loadLevel(int levelIndex) {
        this.levelConfig = LevelConfigLoader.getConfig(levelIndex);
        if (levelConfig.buffer != null) {
            runtimeContext.setBufferConfig(levelConfig.buffer.instIn, levelConfig.buffer.instOut);
        }
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    // --- 核心注册及索引建立逻辑 ---
    private void registerInstruction(String structId, String instId, String pattern, int maxUses) {
        InstructionDefinition def = new InstructionDefinition(structId, instId, pattern);
        def.setMaxUses(maxUses);
        int newIndex = instructionTable.size();
        instructionTable.add(def);
        identityToIndex.put(structId + "_" + instId, newIndex);
        
        // 由于下标自增，列表天然有序
        structToIndices.computeIfAbsent(structId, k -> new ArrayList<>()).add(newIndex);
    }

    public void registerStructures() {
        System.out.println("\n[Debug] === 阶段一: 加载物理结构与挂载基础指令 ===");
        com.algoblock.config.StructureRegistry registryConfig = com.algoblock.util.StructureRegistryLoader.getRegistry();
        Map<String, String> registry = new HashMap<>();
        
        for (String struct : levelConfig.structUsed) {
            String relativePath = registryConfig.getPath(struct);
            if (relativePath == null) {
                throw new RuntimeException("配置错误：在注册表实例中未找到结构体 [" + struct + "] 的定义");
            }
            String dotNotation = relativePath.replace('/', '.');
            if (dotNotation.endsWith(".java")) dotNotation = dotNotation.substring(0, dotNotation.length() - 5);
            registry.put(struct, "com.algoblock.structure." + dotNotation);
        }
        
        for (String struct : levelConfig.structUsed) {
            String fqcn = registry.get(struct);
            try {
                Abstract structInstance = (Abstract) Class.forName(fqcn).getDeclaredConstructor().newInstance();
                structureTemplates.put(struct, structInstance);

                Map<String, String> patternsMap = structInstance.getPatterns();
                for (Map.Entry<String, String> entry : patternsMap.entrySet()) {
                    registerInstruction(struct, entry.getKey(), entry.getValue(), 0); // 默认配额为0
                    System.out.println("[Debug] -> [" + struct + "] 成功挂载预设指令: " + entry.getKey() + " | Pattern: " + entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("[Debug] [异常] 结构体注册严重失败: " + fqcn);
            }
        }
    }

    public void initAllowedLimits() {
        System.out.println("\n[Debug] === 阶段二: 遍历授权名单更新权限 / 动态拉取新指令 ===");
        if (levelConfig.instsAllowed == null) return;

        for (LevelConfig.InstConfig instConfig : levelConfig.instsAllowed) {
            String structId = instConfig.struct;
            String instId = instConfig.instId;
            String identityKey = structId + "_" + instId;

            System.out.println("[Debug] 处理授权清单: 结构[" + structId + "] - 指令[" + instId + "] -> 授权配额: " + instConfig.maxUses);

            Integer index = identityToIndex.get(identityKey);
            System.out.println("[Debug]   |- 预设缓存匹配情况: " + (index != null));

            if (index != null) {
                instructionTable.get(index).setMaxUses(instConfig.maxUses);
                System.out.println("[Debug]   |- [完毕] 仅更新配额上限");
            } else {
                System.out.println("[Debug]   |- 触发向下层架构反射请求");
                Abstract structTemplate = structureTemplates.get(structId);
                if (structTemplate != null) {
                    boolean loaded = structTemplate.loadMethodDynamically(instId);
                    System.out.println("[Debug]   |- 底层架构反馈加载结果: " + loaded);
                    if (loaded) {
                        String pattern = structTemplate.getPatterns().get(instId);
                        registerInstruction(structId, instId, pattern, instConfig.maxUses);
                        System.out.println("[Debug]   |- [完毕] 指令已动态组装加入引擎库. Pattern: " + pattern);
                    }
                }
            }
        }
    }

    // --- 核心执行判断逻辑 ---
    private String extractStructId(String statement) {
        int firstParen = statement.indexOf('(');
        int firstDot = statement.indexOf('.');
        if (firstParen != -1 && firstDot != -1) return statement.substring(0, Math.min(firstParen, firstDot));
        else if (firstParen != -1) return statement.substring(0, firstParen);
        else if (firstDot != -1) return statement.substring(0, firstDot);
        return statement;
    }

    private String[] extractArgumentsFast(InstructionDefinition def, String statement) {
        String[] literals = def.getLiterals();
        List<String> args = new ArrayList<>(literals.length - 1);
        int cursor = 0;

        for (int i = 0; i < literals.length; i++) {
            String lit = literals[i];
            if (i == 0) {
                if (!statement.startsWith(lit)) return null;
                cursor = lit.length();
            } else {
                if (lit.isEmpty()) {
                    args.add(statement.substring(cursor));
                    cursor = statement.length();
                } else {
                    int matchIdx = statement.indexOf(lit, cursor);
                    if (matchIdx == -1) return null;
                    args.add(statement.substring(cursor, matchIdx));
                    cursor = matchIdx + lit.length();
                }
            }
        }
        if (cursor != statement.length()) return null;
        return args.toArray(new String[0]);
    }

    public boolean executeStatement(String statement, boolean isPlayerAction) {
        System.out.println("[Debug] >>> 引擎开始路由分析语句: " + statement + " | 执行主体: " + (isPlayerAction ? "玩家" : "系统"));

        String structId = extractStructId(statement);
        System.out.println("[Debug] -> O(1) 预检定位到的主结构体 ID 为: " + structId);

        List<Integer> indices = structToIndices.get(structId);
        if (indices == null) {
            System.out.println("[Debug] -> [拦截] 未找到结构体 [" + structId + "] 的相关指令库");
            return false;
        }

        for (int idx : indices) {
            InstructionDefinition def = instructionTable.get(idx);
            
            if (isPlayerAction) {
                if (def.getMaxUses() <= 0) continue;
                if (def.getUsedCount() >= def.getMaxUses()) continue;
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
                runtimeContext.setIsPlayerAction(isPlayerAction);
                template.executeInstruction(def.getInstId(), args, runtimeContext);
                runtimeContext.setIsPlayerAction(false);
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
    // 纯后端功能：词法提取与指令状态分析
    // ==========================================

    private void generateTypedPaths(String[] literals, int varIdx, List<TypedToken> currentPath, Set<String> activeVars, List<List<TypedToken>> result) {
        List<TypedToken> nextPath = new ArrayList<>(currentPath);
        if (!literals[varIdx].isEmpty()) nextPath.add(new TypedToken(literals[varIdx], '1'));

        if (varIdx == literals.length - 1) {
            result.add(nextPath);
            return;
        }
        for (String var : activeVars) {
            List<TypedToken> pathWithVar = new ArrayList<>(nextPath);
            pathWithVar.add(new TypedToken(var, '2'));
            generateTypedPaths(literals, varIdx + 1, pathWithVar, activeVars, result);
        }
    }

    private String buildEncodingString(List<TypedToken> path, int targetLen) {
        StringBuilder sb = new StringBuilder();
        int currentLen = 0;
        for (TypedToken t : path) {
            if (currentLen >= targetLen) break;
            int take = Math.min(t.text.length(), targetLen - currentLen);
            sb.append(String.valueOf(t.type).repeat(take));
            currentLen += take;
        }
        return sb.toString();
    }

    private TypedToken extractNextComponent(List<TypedToken> path, int targetLen) {
        int currentLen = 0;
        for (TypedToken t : path) {
            if (currentLen >= targetLen) return t; // 整个节点均在目标长度之后
            int end = currentLen + t.text.length();
            if (targetLen > currentLen && targetLen < end) {
                // 截断在当前节点内部，返回剩余的半截及其成分类型
                return new TypedToken(t.text.substring(targetLen - currentLen), t.type);
            }
            currentLen = end;
        }
        return null;
    }

    public LexicalAnalysisResult analyzeInstruction(String input) {
        LexicalAnalysisResult result = new LexicalAnalysisResult();
        result.status = InstructionStatus.ERROR;
        result.inputEncoding = "";
        
        boolean hasExact = false;
        boolean hasExtensions = false;
        Map<String, LexicalComponent> uniqueNextTokens = new LinkedHashMap<>();
        String bestInputEncoding = null;

        // 前缀优化：锁定结构列表
        String structIdPrefix = extractStructId(input);
        List<Integer> candidateIndices = new ArrayList<>();
        
        if (structToIndices.containsKey(structIdPrefix) && (input.contains("(") || input.contains("."))) {
            candidateIndices.addAll(structToIndices.get(structIdPrefix));
        } else {
            // 没有分隔符时则可能尚未打全，检查所有以前缀开头的映射
            for (Map.Entry<String, List<Integer>> entry : structToIndices.entrySet()) {
                if (entry.getKey().startsWith(structIdPrefix) || structIdPrefix.isEmpty()) {
                    candidateIndices.addAll(entry.getValue());
                }
            }
        }

        for (int idx : candidateIndices) {
            InstructionDefinition def = instructionTable.get(idx);
            // 后端分析校验权限，排除不可用的联想
            if (def.getMaxUses() > 0 && def.getUsedCount() >= def.getMaxUses()) continue;

            Set<String> activeVars = runtimeContext.getActiveObjectNames(def.getStructId());
            List<List<TypedToken>> paths = new ArrayList<>();
            generateTypedPaths(def.getLiterals(), 0, new ArrayList<>(), activeVars, paths);

            for (List<TypedToken> path : paths) {
                StringBuilder fullStrBuilder = new StringBuilder();
                for (TypedToken t : path) fullStrBuilder.append(t.text);
                String fullStr = fullStrBuilder.toString();

                if (fullStr.equals(input)) {
                    hasExact = true;
                    if (bestInputEncoding == null) bestInputEncoding = buildEncodingString(path, input.length());
                } else if (fullStr.startsWith(input)) {
                    hasExtensions = true;
                    if (bestInputEncoding == null) bestInputEncoding = buildEncodingString(path, input.length());

                    TypedToken nextComponent = extractNextComponent(path, input.length());
                    if (nextComponent != null && !uniqueNextTokens.containsKey(nextComponent.text)) {
                        String enc = String.valueOf(nextComponent.type).repeat(nextComponent.text.length());
                        uniqueNextTokens.put(nextComponent.text, new LexicalComponent(nextComponent.text, enc));
                    }
                }
            }
        }

        // 最终定性封装
        if (hasExact && hasExtensions) result.status = InstructionStatus.MATCHED_WITH_EXTENSIONS;
        else if (hasExact) result.status = InstructionStatus.EXACT_MATCH;
        else if (hasExtensions) result.status = InstructionStatus.INCOMPLETE;
        else result.status = InstructionStatus.ERROR;

        result.inputEncoding = bestInputEncoding != null ? bestInputEncoding : "";
        result.nextOptions.addAll(uniqueNextTokens.values());

        return result;
    }

    public void run(int levelIndex) {
        loadLevel(levelIndex);
        registerStructures();
        initAllowedLimits();

        System.out.println("\n--- 初始化阶段 ---");
        for (String initInst : levelConfig.initInsts) {
            executeStatement(initInst, false);
        }

        System.out.println("\n--- 进入关卡主循环 ---");
        Scanner scanner = new Scanner(System.in);
        int currentStep = 0;
        
        // 分离出的输入控制层
        InputController inputController = new InputController(this);

        while (currentStep < levelConfig.stepsLimit) {
            System.out.println("\n[步骤1] 归零游戏对象栈的两个变量");
            runtimeContext.resetCheckCounts();

            System.out.println("[步骤2] 等待一个语句输入 (请输入单字符，或控制命令 [tab, del, enter, up, down]):");
            
            // 调度上层交互界面获取指令
            String input = inputController.interactiveReadCommand(scanner);
            if ("exit".equalsIgnoreCase(input.trim())) break;

            System.out.println("[步骤3] 检查该语句是否可执行");
            boolean executed = executeStatement(input, true);
            if (!executed) continue;

            System.out.println("[步骤5] 执行完后对judge_insts里的语句顺序逐一执行");
            for (String judge : levelConfig.judgeInsts) {
                executeStatement(judge, false);
            }

            System.out.println("[步骤6] 清空游戏对象栈的缓冲区");
            runtimeContext.clearBuffer();

            System.out.println("[步骤7] 判断是否退出大循环");
            if (runtimeContext.isWinConditionMet()) {
                System.out.println("[过关] 判定条件通过！游戏胜利！");
                break;
            } else {
                System.out.println("[循环] 判定未通过，继续循环。剩余步数: " + (levelConfig.stepsLimit - currentStep - 1));
            }
            currentStep++;
        }

        if (currentStep >= levelConfig.stepsLimit && !runtimeContext.isWinConditionMet()) {
            System.out.println("[结算] 达到最大步数限制，游戏结束。");
        }
        scanner.close();
    }
}

// ==========================================
// 隔离解耦：独立分离出的交互UI层
// ==========================================
class InputController {
    private final Core core;

    public InputController(Core core) {
        this.core = core;
    }

    public String interactiveReadCommand(Scanner scanner) {
        StringBuilder buffer = new StringBuilder();
        int selectedOptionIndex = 0;

        while (true) {
            // 将纯准指令字符串传递给下层，拿到带分析编码的返回体
            LexicalAnalysisResult analysis = core.analyzeInstruction(buffer.toString());
            
            String inputColor;
            boolean isExactMatch = false;

            // 根据返回状态进行UI渲染反馈
            switch (analysis.status) {
                case EXACT_MATCH:
                case MATCHED_WITH_EXTENSIONS:
                    isExactMatch = true;
                    inputColor = TerminalUtils.GREEN;
                    break;
                case INCOMPLETE:
                    inputColor = TerminalUtils.WHITE;
                    break;
                case ERROR:
                default:
                    inputColor = TerminalUtils.RED;
                    break;
            }

            TerminalUtils.clearCurrentLine();
            System.out.print("\r\033[0J");
            System.out.print("> " + inputColor + buffer.toString() + TerminalUtils.RESET);

            List<LexicalComponent> optionsList = analysis.nextOptions;

            // 显示后续备选联想
            if (!isExactMatch && !optionsList.isEmpty()) {
                if (optionsList.size() == 1) {
                    System.out.print(TerminalUtils.GOLD + optionsList.get(0).text + TerminalUtils.RESET);
                } else {
                    System.out.println();
                    for (int i = 0; i < optionsList.size(); i++) {
                        if (i == selectedOptionIndex) {
                            System.out.println(TerminalUtils.GOLD + "[" + optionsList.get(i).text + "]" + TerminalUtils.RESET);
                        } else {
                            System.out.println(TerminalUtils.GRAY + optionsList.get(i).text + TerminalUtils.RESET);
                        }
                    }
                    TerminalUtils.clearBelowAndRenderUp(optionsList.size(), 2 + buffer.length());
                }
            }

            // 处理字符与动作分发
            String action = scanner.nextLine().trim();

            if ("enter".equalsIgnoreCase(action)) {
                System.out.println();
                return buffer.toString();
            } else if ("tab".equalsIgnoreCase(action)) {
                if (!optionsList.isEmpty()) {
                    int idx = (optionsList.size() == 1) ? 0 : selectedOptionIndex;
                    buffer.append(optionsList.get(idx).text);
                    selectedOptionIndex = 0;
                }
            } else if ("del".equalsIgnoreCase(action)) {
                if (buffer.length() > 0) buffer.deleteCharAt(buffer.length() - 1);
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
}