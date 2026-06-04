package com.algoblock.structure.queue.method;

import com.algoblock.RuntimeContext;
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
            for (String v : values.split(",")) {
                newObj.enqueue(Integer.parseInt(v));
            }
        }
        context.putObject(FakeQueue.TYPE_ID, objName, newObj);
    
    }
}