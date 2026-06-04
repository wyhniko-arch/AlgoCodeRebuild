package com.algoblock.structure.queue.method;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.queue.FakeQueue;

public class Delete implements StructureMethod {
    private static final String PATTERN = "Queue(@).delete";

    @Override
    public String getPattern() { return PATTERN; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        context.removeObject(FakeQueue.TYPE_ID, objName);
    }
}