package com.algoblock.structure;

import com.algoblock.context.RuntimeContext;

public interface StructureMethod {

    /**
     * 返回带有通配符的模板字符串。
     * 括号 () 具有层级含义，@ 的内容由括号深度界定。
     * 例如：
     *   "Queue@.pop"        — 一个 @，代表对象名
     *   "Stack@.copy@"      — 两个 @，第一个是源对象名，第二个是目标对象名
     *   "Command.In@"       — 一个 @，代表要写入的完整指令字符串
     */
    String getPattern();

    /**
     * 与 getPattern() 中 @ 数量严格对应的语义描述符数组。
     * 词法分析阶段据此决定候选枚举策略：
     *
     *   "any"              — 不限制，自由输入，不产生候选
     *   "obj[A,B,...]"     — 从结构 ID 为 A 或 B 的存活对象名称中选
     *   "cmd[]"            — 从所有已授权指令的展开形式中选
     *   "cmd[tag1,tag2]"   — 从带有指定标签的已授权指令的展开形式中选
     *
     * 数组长度必须等于 getPattern() 中 @ 的数量。
     */
    String[] getArgHints();

    /**
     * 该指令的标签组（可空数组）。
     * 用于被其他指令的 cmd[tag] 语义引用，以及运行期过滤。
     * 典型标签：
     *   "in"   — 该指令可作为 bufferCommandIn（会消费缓冲区输出）
     *   "out"  — 该指令可作为 bufferCommandOut（会向缓冲区推送输出）
     */
    String[] getTags();

    /**
     * 执行指令。
     *
     * @param args    由引擎统一提取的参数数组（与 @ 数量对应）
     * @param context 运行时上下文
     */
    void execute(String[] args, RuntimeContext context);
}