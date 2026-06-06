package com.algoblock.structure.queue.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.queue.FakeQueue;

public class InitFull implements StructureMethod {
    private static final String PATTERN = "Queue(@,(@))";

    @Override
    public String getPattern() { return PATTERN; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        String objName =args[0];
        String values = args[1];
        FakeQueue newObj = new FakeQueue();
        newObj.name = objName;
        
        if (!values.isEmpty()) {
            // [可变变量（业务逻辑）]: 批量初始化写入。按顺序直接分配内存及修改指针
            for (String v : values.split(",")) {
                int val = Integer.parseInt(v);
                if (newObj.size == newObj.array.length) {
                    newObj.resizeRawArray(newObj.array.length * 2);
                }
                newObj.array[newObj.tail] = val;
                newObj.tail = (newObj.tail + 1) % newObj.array.length;
                newObj.size++;
            }
        }
        context.putObject(FakeQueue.TYPE_ID, objName, newObj);
    }
}