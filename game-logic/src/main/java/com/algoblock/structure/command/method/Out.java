package com.algoblock.structure.command.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.tools.buffer.RowBuffer;

/**
 * Command.out[@]
 * @ 语义：cmd[out] — 必须是已注册且带 "out" 标签的指令的完整展开
 */
public class Out implements StructureMethod {
    private static final String   PATTERN   = "Command.out[@]";
    private static final String[] ARG_HINTS = {"cmd[out]"};
    private static final String[] TAGS      = {};

    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        String newCommandOut = args[0];
        context.setBufferCommandOut(newCommandOut);
        RowBuffer.append("[Command] bufferCommandOut 已更新为: " + newCommandOut);
    }
}