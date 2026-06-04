package com.algoblock.structure.stack.method;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class Copy implements StructureMethod {
    private static final String PATTERN = "Stack(@).copy(@)";
    @Override public String getPattern() { return PATTERN; }
    @Override public void execute(String[] args, RuntimeContext context) {
        String srcName = args[0];
        String destName = args[1];
        FakeStack srcObj = (FakeStack) context.getObject(FakeStack.TYPE_ID, srcName);
        if (srcObj != null) {
            FakeStack newObj = new FakeStack();
            newObj.name = destName;
            newObj.array = new int[srcObj.array.length];
            System.arraycopy(srcObj.array, 0, newObj.array, 0, srcObj.array.length);
            newObj.top = srcObj.top;
            context.putObject(FakeStack.TYPE_ID, newObj.name, newObj);
        }
    }
}