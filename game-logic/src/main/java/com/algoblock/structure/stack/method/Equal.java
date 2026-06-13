package com.algoblock.structure.stack.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class Equal implements StructureMethod {
    private static final String   PATTERN   = "Stack.equal[@,@]";
    private static final String[] ARG_HINTS = {"obj[Stack]", "obj[Stack]"};
    private static final String[] TAGS      = {};
    @Override public String getPattern()    { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        String nameA = args[0];
        String nameB = args[1];
        FakeStack.Instance objA = (FakeStack.Instance) context.getObject(FakeStack.STRUCTURE_ID, nameA);
        FakeStack.Instance objB = (FakeStack.Instance) context.getObject(FakeStack.STRUCTURE_ID, nameB);
        context.incrementRunCheck();
        if (objA != null && objB != null && objA.top == objB.top) {
            boolean isEqual = true;
            // 线性数组比对，直到栈顶
            for (int i = 0; i <= objA.top; i++) {
                if (objA.array[i] != objB.array[i]) {
                    isEqual = false;
                    break;
                }
            }
            if (isEqual) context.incrementPassedCheck();
        }
    }
}