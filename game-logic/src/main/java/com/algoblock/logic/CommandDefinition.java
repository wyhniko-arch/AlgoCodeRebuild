package com.algoblock.logic;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
 
/**
 * 一条已注册指令的运行时描述符。
 * 持有：来源结构ID、指令ID、pattern、参数语义描述（argHints）、标签组（tags）、使用配额。
 */
public class CommandDefinition {

    private final String structId;
    private final String commandId;
    private final String pattern;

    // pattern 按 @ 拆成字面量片段（括号仍为普通字符，提取参数时由 PatternMatcher 感知括号深度）
    private final String[] literals;

    // 每个 @ 对应的语义描述符，长度 == literals.length - 1
    private final String[] argHints;

    // 该指令的标签集合，用于 cmd[tag] 语义过滤
    private final Set<String> tags;

    private int usedCount;
    private int maxUses;

    public CommandDefinition(String structId, String commandId, String pattern,
                             String[] argHints, String[] tags) {
        this.structId = structId;
        this.commandId = commandId;
        this.pattern = pattern;
        // 使用 -1 保留尾部空字符串，严格划定交替边界
        this.literals = pattern.split("@", -1);
        this.argHints = (argHints != null) ? argHints : new String[0];
        this.tags = (tags != null && tags.length > 0)
                ? Collections.unmodifiableSet(new HashSet<>(Arrays.asList(tags)))
                : Collections.emptySet();
        this.usedCount = 0;
        this.maxUses = 0;
    }

    // ---- 读取方法 ----
    public String getStructId() { return structId; }
    public String getCommandId() { return commandId; }
    public String getPattern() { return pattern; }
    public String[] getLiterals() { return literals; }
    public String[] getArgHints() { return argHints; }
    public Set<String> getTags() {return tags; }

    public boolean hasTag(String tag) {return tags.contains(tag); }
    /** 参数个数 = @ 数量 = literals.length - 1 */
    public int getArgCount() { return literals.length -1; }

    // ---- 使用配额 ----
    public int getUsedCount() { return usedCount; }
    public void incrementUsedCount() { this.usedCount++; }
    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }
    public void resetUses() { this.usedCount = 0; }
}