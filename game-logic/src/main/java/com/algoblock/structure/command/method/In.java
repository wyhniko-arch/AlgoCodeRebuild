package com.algoblock.structure.command.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.tools.buffer.RowBuffer;

/**
 * Command.in[@]
 * @ 语义：cmd[in] — 必须是已注册且带 "in" 标签的指令的完整展开
 */
public class In implements StructureMethod {
    private static final String   PATTERN   = "Command.in[@]";
    private static final String[] ARG_HINTS = {"cmd[in]"};
    private static final String[] TAGS      = {};

    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        String newCommandIn = args[0];
        context.setBufferCommandIn(newCommandIn);
        RowBuffer.append("[Command] bufferCommandIn 已更新为: " + newCommandIn);
    }
}