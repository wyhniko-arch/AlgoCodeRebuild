package com.algoblock.structure.stack.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class InitEmpty implements StructureMethod {
    private static final String PATTERN = "Stack(@)";
    
    @Override 
    public String getPattern() { 
        return PATTERN; 
    }
    
    @Override 
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        FakeStack newObj = new FakeStack();
        newObj.name = objName; // 分配栈对象名称
        context.putObject(FakeStack.TYPE_ID, newObj.name, newObj);
    
    }
}