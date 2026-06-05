package com.algoblock.structure.stack.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class Push implements StructureMethod {
    private static final String PATTERN = "Stack(@).push";
    
    @Override 
    public String getPattern() { return PATTERN; }
    
    @Override 
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeStack obj = (FakeStack) context.getObject(FakeStack.TYPE_ID, objName);
        if (obj != null) {
            if (context.getandresetIsPlayerAction()) {
                context.triggerEngineCommand(context.getBufferInstOut()); 
            }
            Integer val = context.popFromBuffer();
            if (val != null) {
                // [可变变量（业务逻辑）]: 压栈逻辑。扩容策略与指针自增完全由策略类控制
                if (obj.top == obj.array.length - 1) {
                    obj.resizeRawArray(obj.array.length * 2);
                }
                obj.array[++obj.top] = val;
            }
        }
    }
}