package com.algoblock.structure.queue.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.queue.FakeQueue;

public class InitEmpty implements StructureMethod {
    private static final String   PATTERN   = "Queue[@]";
    private static final String[] ARG_HINTS = {"any"};
    private static final String[] TAGS      = {};
    @Override public String getPattern()    { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeQueue.Instance newObj = new FakeQueue.Instance();
        newObj.name = objName;
        context.putObject(FakeQueue.STRUCTURE_ID, objName, newObj);
    }
}