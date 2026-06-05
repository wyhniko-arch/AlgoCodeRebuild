package com.algoblock.core;

public class InstructionDefinition {
    private final String structId;
    private final String commandId;
    private final String pattern;
    // 预处理拆分的常量片段数组
    private final String[] literals;
    private int usedCount;
    private int maxUses;

    public InstructionDefinition(String structId, String commandId, String patternStr) {
        this.structId = structId;
        this.commandId = commandId;
        this.pattern = patternStr;
        // 使用 -1 保留尾部空字符串，严格划定交替边界
        this.literals = patternStr.split("@", -1);
        this.usedCount = 0;
        this.maxUses = 0;
    }

    public String getStructId() { return structId; }
    public String getCommandId() { return commandId; }
    public String getPattern() { return pattern; }
    public String[] getLiterals() { return literals; }
    
    public int getUsedCount() { return usedCount; }
    public void incrementUsedCount() { this.usedCount++; }
    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }
    public void resetUses() { this.usedCount = 0; }
}