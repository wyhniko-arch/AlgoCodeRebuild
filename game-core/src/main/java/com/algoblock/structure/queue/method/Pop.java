package com.algoblock.structure.queue.method;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.queue.FakeQueue;

public class Pop implements StructureMethod {
    private static final String PATTERN = "Queue(@).pop";

    @Override
    public String getPattern() { return PATTERN; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeQueue obj = (FakeQueue) context.getObject(FakeQueue.TYPE_ID, objName);
        if (obj != null) {
            if (obj.size > 0) {
                context.pushToBuffer(obj.dequeue());
            }
            if (context.getandresetIsPlayerAction()) { //如果是玩家指令则触发连锁
                context.triggerEngineCommand(context.getBufferInstIn());
            }
        }
    }
}