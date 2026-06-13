package com.algoblock.logic;

import com.algoblock.context.RuntimeContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * argHint 语义解析器：负责把单个 @ 位置的语义描述符（hint）转化为词法补全后缀列表
 * 或执行时的合法性校验结果。
 *
 * 设计要点：
 *   1. hint 语法可扩展：每种 hint（obj、cmd、any、未来可能的 cmd[...][...]）由一个
 *      内部静态 spec 类负责解析与展开，主流程通过 dispatch 选择 spec。
 *   2. 配额感知：词法补全分两层——
 *        a) 最外层（玩家正在输入的语句根）：只考虑玩家有配额的指令
 *        b) cmd[] 内部的递归层：仅做"语法合法性"判断，不检查配额（即使配额为 0 的指令
 *           只要存在就算合法语义）。该差异由 boolean checkQuota 参数贯穿递归全程。
 *   3. 候选去重：同一个补全后缀字符串可能由多条不同 def / 不同分支产生，统一用
 *      LinkedHashSet 去重并保序。
 *   4. 输出契约：所有面向"补全后缀"的方法（resolveCompletion、completionsForDef）
 *      返回的字符串是可直接追加到用户当前 inputBuffer 上的 raw 片段；外层右括号
 *      已经被正确包含在内（见 completionsForDef 的"收尾闭括号"段）。
 *
 * 公开入口：
 *   - resolve(hint, context, allDefs)            完整候选枚举（带外层括号）
 *   - resolveCompletion(hint, soFar, ctx, defs)  词法补全后缀（带括号/包含闭括号）
 *   - validateArg(hint, argValue, ctx, defs)     执行时校验单个参数
 *   - completionsForDef(def, input, checkQuota, ctx, defs)
 *                                                对单条 def 递归推进的补全（核心引擎）
 */
public final class ArgHintResolver {

    private ArgHintResolver() {}

    // ==========================================
    // 1) hint 语法分发：扩展点在此
    // ==========================================

    /**
     * 注册的 ArgHintSpec 列表，按声明顺序匹配第一个 accepts() 返回 true 的。
     * 将来扩展 hint 语法（如 cmd[...][...]）只需新增一个 Spec 实现并加入此列表。
     */
    private static final ArgHintSpec[] SPECS = new ArgHintSpec[] {
            new AnySpec(),
            new ObjSpec(),
            new CmdSpec(),
    };

    /** 选出能处理此 hint 的 spec；找不到返回 AnySpec（兜底）。 */
    private static ArgHintSpec dispatch(String hint) {
        if (hint == null) return SPECS[0]; // any
        for (ArgHintSpec s : SPECS) if (s.accepts(hint)) return s;
        return SPECS[0];
    }

    // ==========================================
    // 2) 三个对外入口
    // ==========================================

    /**
     * 完整候选枚举：列出所有可能的合法参数值（带外层括号）。
     * 用于"用户尚未输入任何东西，列出所有可选项"或被 expandPattern 使用。
     */
    public static List<String> resolve(
            String hint, RuntimeContext context,
            Map<String, List<CommandDefinition>> allDefs) {
        return dispatch(hint).enumerate(hint, context, allDefs);
    }

    /**
     * 词法补全：用户在某个 @ 的 '(' 之后已经输入 soFar，求下一步可追加的后缀列表。
     * 注意：返回的后缀已经包含必要的外层右括号（当补全应使语句完整时）。
     *
     * @param hint    @ 的语义描述符
     * @param soFar   用户在 '(' 之后已输入的字符串
     * @param context 运行时上下文
     * @param allDefs 全量已授权指令（按配额过滤后的）
     */
    public static List<String> resolveCompletion(
            String hint, String soFar,
            RuntimeContext context,
            Map<String, List<CommandDefinition>> allDefs) {
        if (soFar == null) soFar = "";
        return dispatch(hint).completions(hint, soFar, context, allDefs);
    }

    /** 执行时单参数语义校验。 */
    public static boolean validateArg(
            String hint, String argValue,
            RuntimeContext context,
            Map<String, List<CommandDefinition>> allDefs) {
        return dispatch(hint).validate(hint, argValue, context, allDefs);
    }

    // ==========================================
    // 3) 核心引擎：对单条 def 在子上下文中做词法推进
    // ==========================================

    /**
     * 对单条 CommandDefinition 在子输入 input 下做词法推进，返回所有可追加的后缀。
     * 这是 cmd[] 递归的根操作——它扮演的角色等同于 Logic.computeOptions 对一条 def 的
     * 处理，但参数化了"是否检查配额"。
     *
     * @param def          要推进的指令定义
     * @param input        当前子输入（外层 @ 的 '(' 之后的内容）
     * @param checkQuota   true=最外层（玩家执行域），需检查 maxUses；false=cmd[] 内部
     *                     语义匹配，不检查
     * @return 可直接追加到 input 后面的字符串后缀列表
     */
    static List<String> completionsForDef(
            CommandDefinition def, String input, boolean checkQuota,
            RuntimeContext context, Map<String, List<CommandDefinition>> allDefs) {

        // 配额过滤（仅最外层执行）
        if (checkQuota && !hasQuota(def)) return Collections.emptyList();

        // 前缀合法性
        if (!PatternMatcher.couldBePrefix(def, input)) return Collections.emptyList();

        List<String> results = new ArrayList<>();
        List<PatternMatcher.CompletionCandidate> cands =
                PatternMatcher.nextCandidates(def, input);

        for (PatternMatcher.CompletionCandidate c : cands) {
            if (!c.isArgHint()) {
                if (!c.literal.isEmpty()) results.add(c.literal);
            } else {
                String subHint  = (c.argIndex < def.getArgHints().length)
                        ? def.getArgHints()[c.argIndex] : "any";
                String subSoFar = c.inputSoFar == null ? "" : c.inputSoFar;
                // 递归子 @：cmd[] 内部递归时会切到 checkQuota=false 语义
                results.addAll(dispatch(subHint).completions(subHint, subSoFar, context, allDefs));
            }
        }
        return results;
    }

    // ==========================================
    // 4) 辅助工具
    // ==========================================

    /** 按 cmd[inner] 的 inner 规则筛选 def 列表（StructId / StructId.cmdId 的并集）。 */
    static List<CommandDefinition> filterDefs(
            String inner, Map<String, List<CommandDefinition>> allDefs) {

        if (inner.isEmpty()) {
            List<CommandDefinition> result = new ArrayList<>();
            for (List<CommandDefinition> defs : allDefs.values()) result.addAll(defs);
            return result;
        }

        List<CommandDefinition> result = new ArrayList<>();
        Set<CommandDefinition> seen = new HashSet<>();
        for (String token : inner.split(",")) {
            token = token.trim();
            if (token.isEmpty()) continue;
            int dot = token.indexOf('.');
            if (dot < 0) {
                List<CommandDefinition> defs = allDefs.get(token);
                if (defs != null)
                    for (CommandDefinition d : defs) if (seen.add(d)) result.add(d);
            } else {
                String structId = token.substring(0, dot).trim();
                String cmdId    = token.substring(dot + 1).trim();
                List<CommandDefinition> defs = allDefs.get(structId);
                if (defs != null)
                    for (CommandDefinition d : defs)
                        if (d.getCommandId().equals(cmdId) && seen.add(d)) result.add(d);
            }
        }
        return result;
    }

    /** 给一组带括号的完整候选做前缀过滤并裁掉前缀。 */
    static List<String> filterAndTrim(List<String> candidates, String soFar) {
        String prefix = "(" + soFar;
        List<String> out = new ArrayList<>();
        for (String c : candidates) if (c.startsWith(prefix)) out.add(c.substring(prefix.length()));
        return out;
    }

    private static boolean hasQuota(CommandDefinition def) {
        return def.getMaxUses() > 0 && def.getUsedCount() < def.getMaxUses();
    }

    /** 对 def 按存活对象做完整笛卡尔积展开（每个 @ 递归用 resolve 取候选）。 */
    public static List<String> expandPattern(
            CommandDefinition def, RuntimeContext context,
            Map<String, List<CommandDefinition>> allDefs) {

        String[] literals = def.getLiterals();
        String[] argHints = def.getArgHints();
        List<String> accum = new ArrayList<>();
        accum.add(literals[0]);

        for (int i = 0; i < def.getArgCount(); i++) {
            String hint = (i < argHints.length) ? argHints[i] : "any";
            List<String> cands = resolve(hint, context, allDefs);

            List<String> next = new ArrayList<>();
            if (cands.isEmpty()) {
                for (String p : accum) next.add(p + "(?)");
            } else {
                for (String p : accum) for (String c : cands) next.add(p + c);
            }
            accum = next;

            String nextLit = literals[i + 1];
            if (!nextLit.isEmpty()) {
                List<String> withLit = new ArrayList<>();
                for (String s : accum) withLit.add(s + nextLit);
                accum = withLit;
            }
        }
        return accum;
    }

    // ==========================================
    // 5) hint 解析策略（每种 hint 一个内部类，扩展点）
    // ==========================================

    /**
     * 单个 @ 的语义描述符的解析策略。
     * 新增一种 hint 语法（如未来 cmd[...][...]）只需实现此接口并加入 SPECS。
     */
    interface ArgHintSpec {
        /** 是否能处理给定的 hint 字符串。 */
        boolean accepts(String hint);

        /** 列出所有合法候选值（带外层括号，如 "(A)"）。 */
        List<String> enumerate(String hint, RuntimeContext ctx,
                               Map<String, List<CommandDefinition>> defs);

        /**
         * 给定用户在 '(' 之后已输入的 soFar，返回可追加的补全后缀列表。
         * 返回的后缀已包含必要的右括号 ')'（用于闭合外层 @ 的定界括号）。
         */
        List<String> completions(String hint, String soFar, RuntimeContext ctx,
                                 Map<String, List<CommandDefinition>> defs);

        /** 校验 argValue（不含外层括号）是否符合此 hint 的约束。 */
        boolean validate(String hint, String argValue, RuntimeContext ctx,
                         Map<String, List<CommandDefinition>> defs);
    }

    // ---- AnySpec：完全自由，不产生候选；validate 永远通过 ----
    static class AnySpec implements ArgHintSpec {
        @Override public boolean accepts(String h) { return h == null || h.equals("any"); }
        @Override public List<String> enumerate(String h, RuntimeContext c,
                                                Map<String, List<CommandDefinition>> d) {
            return Collections.emptyList();
        }
        @Override public List<String> completions(String h, String s, RuntimeContext c,
                                                  Map<String, List<CommandDefinition>> d) {
            return Collections.emptyList();
        }
        @Override public boolean validate(String h, String v, RuntimeContext c,
                                          Map<String, List<CommandDefinition>> d) {
            return true;
        }
    }

    // ---- ObjSpec：obj 或 obj[StructId,...]，名称必须是当前存活对象 ----
    static class ObjSpec implements ArgHintSpec {
        @Override public boolean accepts(String h) {
            return h.equals("obj") || (h.startsWith("obj[") && h.endsWith("]"));
        }
        private Set<String> targetNames(String h, RuntimeContext ctx) {
            if (h.equals("obj")) return ctx.getAllActiveObjectNames();
            String inner = h.substring(4, h.length() - 1);
            Set<String> names = new LinkedHashSet<>();
            for (String id : inner.split(",")) {
                id = id.trim();
                if (!id.isEmpty()) names.addAll(ctx.getActiveObjectNames(id));
            }
            return names;
        }
        @Override public List<String> enumerate(String h, RuntimeContext ctx,
                                                Map<String, List<CommandDefinition>> d) {
            List<String> out = new ArrayList<>();
            for (String n : targetNames(h, ctx)) out.add("(" + n + ")");
            return out;
        }
        @Override public List<String> completions(String h, String s, RuntimeContext ctx,
                                                  Map<String, List<CommandDefinition>> d) {
            return filterAndTrim(enumerate(h, ctx, d), s);
        }
        @Override public boolean validate(String h, String v, RuntimeContext ctx,
                                          Map<String, List<CommandDefinition>> d) {
            return targetNames(h, ctx).contains(v);
        }
    }

    // ---- CmdSpec：cmd 或 cmd[StructId / StructId.cmdId,...] ----
    static class CmdSpec implements ArgHintSpec {
        @Override public boolean accepts(String h) {
            return h.equals("cmd") || (h.startsWith("cmd[") && h.endsWith("]"));
        }
        private String inner(String h) {
            return h.equals("cmd") ? "" : h.substring(4, h.length() - 1);
        }

        @Override
        public List<String> enumerate(String h, RuntimeContext ctx,
                                      Map<String, List<CommandDefinition>> defs) {
            // 完整枚举：递归展开 def 模板（不做配额过滤）
            List<String> out = new ArrayList<>();
            for (CommandDefinition def : filterDefs(inner(h), defs))
                for (String e : expandPattern(def, ctx, defs))
                    out.add("(" + e + ")");
            return out;
        }

        @Override
        public List<String> completions(String h, String soFar, RuntimeContext ctx,
                                        Map<String, List<CommandDefinition>> defs) {
            // ===== cmd[] 内部：以 soFar 为子输入，对每条匹配的 def 推进 =====
            // 关键：递归层 checkQuota=false（语义/语法判定不看配额）
            Set<String> unique = new LinkedHashSet<>();
            List<CommandDefinition> defList = filterDefs(inner(h), defs);

            boolean someExactComplete = false;
            for (CommandDefinition def : defList) {
                // 检查 soFar 本身是否就是 def 的完整匹配——
                // 若是，则 soFar 已构成合法子指令，需追加 ")" 来闭合外层 @
                if (PatternMatcher.extractArgs(def, soFar) != null) {
                    someExactComplete = true;
                }
                // 推进补全
                for (String suffix : completionsForDef(def, soFar, false, ctx, defs)) {
                    unique.add(suffix);
                }
            }

            // 若 soFar 已经是某条 def 的完整匹配，提示用户可以闭合外层括号
            if (someExactComplete) unique.add(")");

            return new ArrayList<>(unique);
        }

        @Override
        public boolean validate(String h, String v, RuntimeContext ctx,
                                Map<String, List<CommandDefinition>> defs) {
            for (CommandDefinition def : filterDefs(inner(h), defs))
                if (PatternMatcher.extractArgs(def, v) != null) return true;
            return false;
        }
    }
}