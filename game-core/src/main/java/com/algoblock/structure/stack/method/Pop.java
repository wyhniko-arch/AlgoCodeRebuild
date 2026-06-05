package com.algoblock.structure.stack.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class Pop implements StructureMethod {
    private static final String PATTERN = "Stack(@).pop";
    
    @Override 
    public String getPattern() { return PATTERN; }
    
    @Override 
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeStack obj = (FakeStack) context.getObject(FakeStack.TYPE_ID, objName);
        if (obj != null) {
            if (obj.top >= 0) {
                // [可变变量（业务逻辑）]: 出栈逻辑。直接下移栈顶指针并提取值
                int val = obj.array[obj.top--];
                context.pushToBuffer(val);
            }
            if (context.getandresetIsPlayerAction()) {
                context.triggerEngineCommand(context.getBufferInstIn()); 
            }
        }
    }
}