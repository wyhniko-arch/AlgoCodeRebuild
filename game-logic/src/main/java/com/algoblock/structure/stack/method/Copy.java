package com.algoblock.structure.stack.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class Copy implements StructureMethod {
    private static final String   PATTERN   = "Stack[@].copy[@]";
    private static final String[] ARG_HINTS = {"obj[Stack]", "any"};
    private static final String[] TAGS      = {};
    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override public void execute(String[] args, RuntimeContext context) {
        String srcName = args[0];
        String destName = args[1];
        FakeStack.Instance srcObj = (FakeStack.Instance) context.getObject(FakeStack.STRUCTURE_ID, srcName);
        if (srcObj != null) {
            FakeStack.Instance newObj = new FakeStack.Instance();
            newObj.name = destName;
            newObj.array = new int[srcObj.array.length];
            System.arraycopy(srcObj.array, 0, newObj.array, 0, srcObj.array.length);
            newObj.top = srcObj.top;
            context.putObject(FakeStack.STRUCTURE_ID, newObj.name, newObj);
        }
    }
}