package com.algoblock.structure.queue.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.queue.FakeQueue;

public class Equal implements StructureMethod {
    private static final String PATTERN = "Queue.equal(@,@)";

    @Override
    public String getPattern() { return PATTERN; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        String nameA = args[0];
        String nameB = args[1];
        FakeQueue.Instance objA = (FakeQueue.Instance) context.getObject(FakeQueue.TYPE_ID, nameA);
        FakeQueue.Instance objB = (FakeQueue.Instance) context.getObject(FakeQueue.TYPE_ID, nameB);
        
        // 执行范式6：无论结果如何，必须增加一次判断总数
        context.incrementRunCheck();
        if (objA != null && objB != null && objA.size == objB.size) {
            boolean isEqual = true;
            // 按队列顺序逐个对比环形数组元素
            for (int i = 0; i < objA.size; i++) {
                int indexA = (objA.head + i) % objA.array.length;
                int indexB = (objB.head + i) % objB.array.length;
                if (objA.array[indexA] != objB.array[indexB]) {
                    isEqual = false;
                    break;
                }
            }
            if (isEqual) {
                context.incrementPassedCheck();
            }
        }
    }
}