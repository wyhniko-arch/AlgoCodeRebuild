package com.algoblock.structure.stack.method;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class Pop implements StructureMethod {
    private static final String PATTERN = "Stack(@).pop";
    @Override public String getPattern() { return PATTERN; }
    @Override public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeStack obj = (FakeStack) context.getObject(FakeStack.TYPE_ID, objName);
        if (obj != null) {
            if (obj.top >= 0) {
                context.pushToBuffer(obj.popVal());
            }
            if (context.getandresetIsPlayerAction()) { //如果是玩家指令则触发连锁
                context.triggerEngineCommand(context.getBufferInstIn()); 
            }
        }
    }
}