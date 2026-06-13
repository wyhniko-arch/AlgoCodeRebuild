package com.algoblock.structure.stack.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class Delete implements StructureMethod {
    private static final String   PATTERN   = "Stack[@].delete";
    private static final String[] ARG_HINTS = {"obj[Stack]"};
    private static final String[] TAGS      = {};
    @Override public String getPattern()    { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override 
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        context.removeObject(FakeStack.STRUCTURE_ID, objName);
    }
}