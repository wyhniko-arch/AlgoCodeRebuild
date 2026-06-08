package com.algoblock.structure.queue.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.queue.FakeQueue;

public class Pop implements StructureMethod {
    private static final String PATTERN = "Queue(@).pop";

    @Override
    public String getPattern() { return PATTERN; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeQueue.Instance obj = (FakeQueue.Instance) context.getObject(FakeQueue.TYPE_ID, objName);
        if (obj != null) {
            if (obj.size > 0) {
                // [可变变量（业务逻辑）]: 出队逻辑，直接修改指针并减小size
                int val = obj.array[obj.head];
                obj.head = (obj.head + 1) % obj.array.length;
                obj.size--;
                
                context.pushToBuffer(val);
            }
            if (context.getandresetIsPlayerAction()) { //如果是玩家指令则触发连锁
                context.triggerEngineCommand(context.getBufferCommandIn());
            }
        }
    }
}