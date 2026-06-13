package com.algoblock.lex;

import com.algoblock.logic.LevelState;
import com.algoblock.tools.buffer.RowBuffer;

import java.util.Arrays;
import java.util.List;

/**
 * 语句执行引擎：解析一行语句，匹配 CommandDefinition，校验 args，路由到结构 Template。
 *
 * 设计为无状态：所有数据通过 LevelState 出入，便于 RuntimeContext / InputHandler 调用。
 *
 * 玩家与系统两种执行身份：
 *   - 玩家（isPlayerAction=true）：必须有配额且未耗尽，匹配成功后 incrementUsedCount
 *   - 系统（isPlayerAction=false）：忽略配额校验，用于 init/judge/triggerEngineCommand
 *
 * 匹配流程：
 *   1. extractStructId：从语句开头 '[' 或 '.' 之前的部分识别结构 ID（O(1) 预检）
 *   2. 在 struct_idToCommands 中拿到候选 def 列表
 *   3. 对每条 def 用 PatternMatcher.extractArgs 做完整括号感知匹配
 *   4. 命中后用 ArgHintResolver.validateArg 逐参语义校验（obj/cmd/any）
 *   5. 委托 Template.executeCommand 真正运行
 */
public final class StatementExecutor {

    private StatementExecutor() {}

    /**
     * 从语句开头提取结构 ID（'[' 或 '.' 之前的子串）。
     * 用于在 struct_idToCommands 中 O(1) 拿到候选指令集。
     */
    static String extractStructId(String statement) {
        int p = statement.indexOf('[');
        int d = statement.indexOf('.');
        if (p != -1 && d != -1) return statement.substring(0, Math.min(p, d));
        if (p != -1) return statement.substring(0, p);
        if (d != -1) return statement.substring(0, d);
        return statement;
    }

    /**
     * 执行一条完整语句。
     *
     * @param state           关卡状态
     * @param statement       完整语句字符串，如 "Queue[(A)].pop"
     * @param isPlayerAction  true=玩家身份（受配额限制），false=系统身份
     * @return 是否成功匹配并执行
     */
    public static boolean execute(LevelState state, String statement, boolean isPlayerAction) {
        RowBuffer.append("[Debug] >>> 路由语句: " + statement
                + " | 主体: " + (isPlayerAction ? "玩家" : "系统"));
        String structId = extractStructId(statement);
        RowBuffer.append("[Debug] -> 定位结构体: " + structId);

        List<CommandDefinition> defs = state.struct_idToCommands.get(structId);
        if (defs == null) {
            RowBuffer.append("[Debug] -> [拦截] 未找到结构体 [" + structId + "] 的指令库");
            return false;
        }

        for (CommandDefinition def : defs) {
            // 玩家身份：先做配额过滤
            if (isPlayerAction) {
                if (def.getMaxUses() <= 0 || def.getUsedCount() >= def.getMaxUses()) continue;
            }

            // 语法匹配：括号感知 + 完全消耗
            String[] args = PatternMatcher.extractArgs(def, statement);
            if (args == null) continue;

            // 语义校验：cmd[]/obj[] 等 hint 必须在当前上下文中合法
            if (!validateArgs(state, def, args)) continue;

            RowBuffer.append("[Debug] -> [匹配] " + def.getCommandId()
                    + " args=" + Arrays.toString(args));

            if (isPlayerAction) {
                def.incrementUsedCount();
                RowBuffer.append("[Debug]    |- 已用: "
                        + def.getUsedCount() + "/" + def.getMaxUses());
            }

            // 把"本次执行身份"传入 RuntimeContext，让结构方法能区分玩家/系统
            state.runtimeContext.setIsPlayerAction(isPlayerAction);
            state.structidToStructure.get(def.getStructId())
                    .executeCommand(def.getCommandId(), args, state.runtimeContext);
            state.runtimeContext.setIsPlayerAction(false);
            return true;
        }

        RowBuffer.append("[Debug] -> [失败] 无匹配 pattern");
        return false;
    }

    /**
     * 对提取出的 args 逐个做语义校验。
     * 每个参数走 ArgHintResolver.validateArg —— 内部按 hint 类型分发到对应 spec：
     *   any 永远通过；obj 检查存活；cmd 检查能匹配某条 def。
     */
    private static boolean validateArgs(LevelState state, CommandDefinition def, String[] args) {
        String[] argHints = def.getArgHints();
        for (int i = 0; i < args.length; i++) {
            String hint = (i < argHints.length) ? argHints[i] : "any";
            if (!ArgHintResolver.validateArg(hint, args[i],
                    state.runtimeContext, state.struct_idToCommands)) {
                RowBuffer.append("[Debug]    |- [语义拒绝] 参数 " + i + " \"" + args[i]
                        + "\" 不符合约束: " + hint);
                return false;
            }
        }
        return true;
    }

    /**
     * 后台触发隐式命令：用于结构方法内部回调（如 Queue.pop 触发 buffer 的 push）。
     * 永远以系统身份执行。
     */
    public static void triggerEngineCommand(LevelState state, String statement) {
        RowBuffer.append("[Debug] *** 引擎触发后台隐式命令：" + statement);
        execute(state, statement, false);
    }
}