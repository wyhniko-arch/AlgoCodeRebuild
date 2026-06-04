package com.algoblock.structure.queue.method;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.queue.FakeQueue;

public class Add implements StructureMethod {
    private static final String PATTERN = "Queue(@).add";

    @Override
    public String getPattern() { return PATTERN; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        // args[0] 对应模板中唯一的一个 @
        String objName = args[0];
        FakeQueue obj = (FakeQueue) context.getObject(FakeQueue.TYPE_ID, objName);
        if (obj != null) {
            if (context.getandresetIsPlayerAction()) { //如果是玩家指令则触发连锁
                context.triggerEngineCommand(context.getBufferInstOut()); 
            }
            Integer val = context.popFromBuffer();
            if (val != null) {
                obj.enqueue(val);
            }
        }
    }
}