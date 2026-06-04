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
                // [可变变量（业务逻辑）]: 指针算法与状态变更完全在策略类中进行控制
                if (obj.size == obj.array.length) {
                    obj.resizeRawArray(obj.array.length * 2);
                }
                obj.array[obj.tail] = val;
                obj.tail = (obj.tail + 1) % obj.array.length;
                obj.size++;
            }
        }
    }
}