package com.algoblock.lex;

import java.util.ArrayList;
import java.util.List;

/**
 * 括号感知的 Pattern 匹配工具。
 *
 * 核心规则：
 *   pattern 中的 @ 在输入里对应 "(" + 内容 + ")"。
 *   括号是定界符，不属于参数内容本身，args[i] 是括号内部的字符串。
 *
 * 例子：
 *   pattern "Queue[@].pop"   input "Queue[(A)].pop"     → args=["A"]   （方括号是字面量）
 *   pattern "Queue@.pop"     input "Queue(A).pop"       → args=["A"]
 *   pattern "Stack@.copy@"   input "Stack(A).copy(B)"   → args=["A","B"]
 *   pattern "Command.In@"    input "Command.In(Queue(A).add)" → args=["Queue(A).add"]
 */
public final class PatternMatcher {

    private PatternMatcher() {}

    // ==========================================
    // 参数提取（完整匹配）
    // ==========================================

    public static String[] extractArgs(CommandDefinition def, String statement) {
        String[] literals = def.getLiterals();
        int argCount = literals.length - 1;
        String[] args = new String[argCount];
        int cursor = 0;

        if (!statement.startsWith(literals[0])) return null;
        cursor += literals[0].length();

        for (int argIdx = 0; argIdx < argCount; argIdx++) {
            if (cursor >= statement.length() || statement.charAt(cursor) != '(') return null;

            int closeIdx = findMatchingClose(statement, cursor);
            if (closeIdx < 0) return null;

            args[argIdx] = statement.substring(cursor + 1, closeIdx);
            cursor = closeIdx + 1;

            String nextLit = literals[argIdx + 1];
            if (!statement.startsWith(nextLit, cursor)) return null;
            cursor += nextLit.length();
        }

        return (cursor == statement.length()) ? args : null;
    }

    /**
     * 从 s[openIdx] 处的 '(' 找到配对的 ')' 下标，深度归零时返回。
     */
    static int findMatchingClose(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    // ==========================================
    // 前缀合法性判断
    // ==========================================

    public static boolean couldBePrefix(CommandDefinition def, String input) {
        String[] literals = def.getLiterals();
        int argCount = literals.length - 1;

        String firstLit = literals[0];
        // input 比 firstLit 短：firstLit 必须以 input 开头
        if (input.length() < firstLit.length()) {
            return firstLit.startsWith(input);
        }
        // input 和 firstLit 等长或更长：必须完全匹配 firstLit 前缀
        if (!input.startsWith(firstLit)) return false;
        int cursor = firstLit.length();

        for (int argIdx = 0; argIdx < argCount; argIdx++) {
            // 截断在 @ 开始处（期待 '('）
            if (cursor >= input.length()) return true;

            if (input.charAt(cursor) != '(') return false;

            int closeIdx = findMatchingClose(input, cursor);
            if (closeIdx < 0) return true; // 括号未闭合，截断在参数内部

            cursor = closeIdx + 1;

            String nextLit = literals[argIdx + 1];
            if (cursor >= input.length()) return true; // 截断在字面量之前

            if (input.startsWith(nextLit, cursor)) {
                cursor += nextLit.length();
            } else {
                // 截断在字面量内部：input 剩余部分必须是 nextLit 的前缀
                return nextLit.startsWith(input.substring(cursor));
            }
        }

        return cursor <= input.length();
    }

    // ==========================================
    // 补全推进
    // ==========================================

    /**
     * 给定当前输入 input，计算下一步可追加的候选片段列表。
     *
     * CompletionCandidate：
     *   isArgHint()==false → literal 可直接追加（字面量片段或 "("）
     *   isArgHint()==true  → 需外部按 argHints[argIndex] 做语义展开；
     *                        inputSoFar 是 '(' 之后已输入的内容（供前缀过滤）
     */
    public static List<CompletionCandidate> nextCandidates(CommandDefinition def, String input) {
        List<CompletionCandidate> result = new ArrayList<>();
        String[] literals = def.getLiterals();
        int argCount = literals.length - 1;

        String firstLit = literals[0];

        // input 比 firstLit 短：补全 firstLit 剩余部分
        if (input.length() < firstLit.length()) {
            if (firstLit.startsWith(input)) {
                result.add(CompletionCandidate.literal(firstLit.substring(input.length())));
            }
            return result;
        }
        // firstLit 必须完全匹配
        if (!input.startsWith(firstLit)) return result;
        int cursor = firstLit.length();

        for (int argIdx = 0; argIdx < argCount; argIdx++) {
            // 截断在 @ 开始处：提示 '('
            if (cursor >= input.length()) {
                result.add(CompletionCandidate.literal("("));
                return result;
            }

            if (input.charAt(cursor) != '(') return result;

            int closeIdx = findMatchingClose(input, cursor);
            if (closeIdx < 0) {
                // 截断在参数内部：转语义展开
                String soFar = input.substring(cursor + 1);
                result.add(CompletionCandidate.argHint(argIdx, soFar));
                return result;
            }

            cursor = closeIdx + 1;

            String nextLit = literals[argIdx + 1];

            // 截断在下一段字面量之前
            if (cursor >= input.length()) {
                if (!nextLit.isEmpty()) result.add(CompletionCandidate.literal(nextLit));
                return result;
            }

            if (input.startsWith(nextLit, cursor)) {
                cursor += nextLit.length();
            } else {
                // 截断在字面量内部
                String remaining = input.substring(cursor);
                if (nextLit.startsWith(remaining)) {
                    result.add(CompletionCandidate.literal(nextLit.substring(remaining.length())));
                }
                return result;
            }
        }

        // 精确匹配，无补全
        return result;
    }

    // ==========================================
    // 辅助数据类
    // ==========================================

    public static class CompletionCandidate {
        public final String literal;
        public final int    argIndex;
        public final String inputSoFar;

        private CompletionCandidate(String literal, int argIndex, String inputSoFar) {
            this.literal    = literal;
            this.argIndex   = argIndex;
            this.inputSoFar = inputSoFar;
        }

        public static CompletionCandidate literal(String s)           { return new CompletionCandidate(s, -1, null); }
        public static CompletionCandidate argHint(int idx, String sf) { return new CompletionCandidate(null, idx, sf); }

        public boolean isArgHint() { return literal == null; }
    }
}