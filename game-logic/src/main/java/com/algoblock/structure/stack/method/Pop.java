package com.algoblock.structure.stack.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class Pop implements StructureMethod {
    private static final String   PATTERN   = "Stack[@].pop";
    private static final String[] ARG_HINTS = {"obj[Stack]"};
    private static final String[] TAGS      = {"out"};
    @Override public String getPattern()    { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override 
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeStack.Instance obj = (FakeStack.Instance) context.getObject(FakeStack.STRUCTURE_ID, objName);
        if (obj != null) {
            if (obj.top >= 0) {
                // [可变变量（业务逻辑）]: 出栈逻辑。直接下移栈顶指针并提取值
                int val = obj.array[obj.top--];
                context.pushToBuffer(val);
            }
            if (context.getandresetIsPlayerAction()) {
                context.triggerEngineCommand(context.getBufferCommandIn()); 
            }
        }
    }
}