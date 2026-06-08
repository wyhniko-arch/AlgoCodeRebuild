package com.algoblock.structure.queue.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.queue.FakeQueue;

public class Copy implements StructureMethod {
    private static final String PATTERN = "Queue(@).copy(@)";

    @Override
    public String getPattern() { return PATTERN; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        String srcName = args[0];
        String destName = args[1];
        FakeQueue.Instance srcObj = (FakeQueue.Instance) context.getObject(FakeQueue.TYPE_ID, srcName);
        if (srcObj != null) {
            FakeQueue.Instance newObj = new FakeQueue.Instance();
            newObj.name = destName;
            newObj.array = new int[srcObj.array.length];
            System.arraycopy(srcObj.array, 0, newObj.array, 0, srcObj.array.length);
            newObj.head = srcObj.head;
            newObj.tail = srcObj.tail;
            newObj.size = srcObj.size;
            context.putObject(FakeQueue.TYPE_ID, destName, newObj);
        }
        
    }
}