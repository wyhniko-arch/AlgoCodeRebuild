package com.algoblock.structure.queue.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.queue.FakeQueue;

public class InitEmpty implements StructureMethod {
    private static final String PATTERN = "Queue(@)";

    @Override
    public String getPattern() { return PATTERN; }

    @Override
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeQueue newObj = new FakeQueue();
        newObj.name = objName;
        context.putObject(FakeQueue.TYPE_ID, objName, newObj);
    }
}