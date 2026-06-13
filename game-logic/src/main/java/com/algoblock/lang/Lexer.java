package com.algoblock.lang;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.algoblock.logic.LevelState;

/**
 * 词法分析器：根据当前 inputBuffer 计算补全候选与状态。
 *
 * 设计为无状态工具类。
 *
 * 算法：对所有玩家有配额的指令运行 PatternMatcher 推进，收集"下一步可追加片段"——
 *   - 字面量片段：直接加入选项
 *   - 参数 hint 片段：交给 ArgHintResolver.completionsForDef 按 hint 类型分发
 *     （obj / cmd / any / 将来的扩展），统一返回带括号闭合的补全后缀
 *
 * 该方法只负责"最外层"的推进；cmd[] 内部的递归在 ArgHintResolver 内部完成。
 * 因此最外层的配额过滤、isExactMatch / isDeadEnd 判定都集中在这里。
 */
public final class Lexer {

    private Lexer() {}

    /** 词法分析结果。 */
    public static class InputAnalysis {
        public boolean      isExactMatch = false;
        public boolean      isDeadEnd    = true;
        public List<String> optionsList  = new ArrayList<>();
    }

    /**
     * 计算当前输入缓冲下的词法补全。
     */
    public static InputAnalysis compute(LevelState state) {
        InputAnalysis result = new InputAnalysis();
        Set<String> uniqueOptions = new LinkedHashSet<>();
        String current = state.inputBuffer.toString();

        for (List<CommandDefinition> defs : state.struct_idToCommands.values()) {
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
                        def, current, true, state.runtimeContext, state.struct_idToCommands);
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
}