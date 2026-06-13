package com.algoblock.logic;

import com.algoblock.api.ResponseBuilder;
import com.algoblock.lex.Lexer;
import com.algoblock.lex.StatementExecutor;
import com.algoblock.tools.buffer.RowBuffer;

import java.util.List;

/**
 * 输入处理器：处理 action:input:<token> 中的所有 token 类型。
 *
 * 设计为无状态：所有数据通过 LevelState 出入；
 * 但 enter 提交后需要通关回写/落盘/切换层级，这些跨模块协调通过 Logic 引用调用，
 * 保持 InputHandler 自身不直接接触 Progress / LevelTree / SaveManager。
 *
 * 支持的 token：
 *   exit                       退出关卡（同 action:reset）
 *   enter                      提交当前输入 → 执行 → 判分 → 胜负判断
 *   tab                        用当前 selected 选项追加到 inputBuffer
 *   del                        删除 inputBuffer 最后一个字符
 *   up / down                  在补全列表中上下移动 selectedOptionIndex
 *   <单字符>                   将该字符追加到 inputBuffer
 */
public final class InputHandler {

    private InputHandler() {}

    /**
     * 处理一次 input token。
     *
     * @param logic  Logic 单例引用（用于跨模块协调）
     * @param state  关卡状态
     * @param action input token 字符串
     * @return 响应字符串数组（多数情况为空数组）
     */
    public static String[] handle(Logic logic, LevelState state, String action) {
        // ---- exit：与 action:reset 等价 ----
        if ("exit".equalsIgnoreCase(action)) {
            return logic.resetLevelExternal();
        }

        // ---- enter：提交语句 → 执行 → 判分 → 胜负 ----
        if ("enter".equalsIgnoreCase(action)) {
            return handleEnter(logic, state);
        }

        // ---- tab：补全 ----
        if ("tab".equalsIgnoreCase(action)) {
            List<String> options = Lexer.compute(state).optionsList;
            if (!options.isEmpty()) {
                int idx = (options.size() == 1) ? 0 : state.selectedOptionIndex;
                idx = Math.min(idx, options.size() - 1);
                state.inputBuffer.append(options.get(idx));
                state.selectedOptionIndex = 0;
            }
            return new String[0];
        }

        // ---- del：退格 ----
        if ("del".equalsIgnoreCase(action)) {
            if (state.inputBuffer.length() > 0) {
                state.inputBuffer.deleteCharAt(state.inputBuffer.length() - 1);
            }
            state.selectedOptionIndex = 0;
            return new String[0];
        }

        // ---- up / down：补全列表光标 ----
        if ("up".equalsIgnoreCase(action)) {
            state.selectedOptionIndex = Math.max(0, state.selectedOptionIndex - 1);
            return new String[0];
        }
        if ("down".equalsIgnoreCase(action)) {
            Lexer.InputAnalysis analysis = Lexer.compute(state);
            state.selectedOptionIndex = Math.min(
                    Math.max(0, analysis.optionsList.size() - 1),
                    state.selectedOptionIndex + 1);
            return new String[0];
        }

        // ---- 单字符：追加到 buffer ----
        if (action.length() == 1) {
            state.inputBuffer.append(action);
            state.selectedOptionIndex = 0;
            return new String[0];
        }

        return ResponseBuilder.error("[Error] 无法识别的 input token: " + action);
    }

    /**
     * 处理 enter：提交语句、执行、判分、胜负判定与回写。
     */
    private static String[] handleEnter(Logic logic, LevelState state) {
        String statement = state.inputBuffer.toString();
        state.inputBuffer.setLength(0);
        state.selectedOptionIndex = 0;

        RowBuffer.append("玩家提交语句: " + statement);
        state.runtimeContext.resetCheckCounts();

        // 语法 + 配额 + 语义校验失败 → 静默吞掉，不消耗步数
        boolean executed = StatementExecutor.execute(state, statement, true);
        if (!executed) return new String[0];

        LevelLoader.executeJudgeCommands(state);
        state.runtimeContext.clearBuffer();

        if (state.runtimeContext.isWinConditionMet()) {
            RowBuffer.append("[过关] 判定条件通过！游戏胜利！");
            logic.handleLevelClearedExternal();
            return ResponseBuilder.win();
        }

        state.currentStep++;
        if (state.currentStep >= state.levelConfig.stepsLimit) {
            RowBuffer.append("[结算] 达到最大步数限制，游戏结束。");
            logic.handleLevelFailedExternal();
            return ResponseBuilder.fail("达到最大步数限制，游戏结束");
        }

        RowBuffer.append("[循环] 判定未通过，继续循环。剩余步数: "
                + (state.levelConfig.stepsLimit - state.currentStep));
        return ResponseBuilder.levelContinue(state.currentStep, state.levelConfig.stepsLimit);
    }
}