package com.algoblock.structure.stack.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class InitEmpty implements StructureMethod {
    private static final String   PATTERN   = "Stack[@]";
    private static final String[] ARG_HINTS = {"any"};
    private static final String[] TAGS      = {};
    @Override public String getPattern()    { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override 
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeStack.Instance newObj = new FakeStack.Instance();
        newObj.name = objName; // 分配栈对象名称
        context.putObject(FakeStack.STRUCTURE_ID, newObj.name, newObj);
    
    }
}