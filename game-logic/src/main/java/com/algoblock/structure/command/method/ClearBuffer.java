package com.algoblock.structure.command.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.tools.buffer.RowBuffer;

public class ClearBuffer implements StructureMethod {
    private static final String   PATTERN   = "Command.clearBuffer";
    private static final String[] ARG_HINTS = {};
    private static final String[] TAGS      = {};

    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        context.clearBuffer();
        RowBuffer.append("[Command] clearBuffer 执行，缓冲区已清空");
    }
}