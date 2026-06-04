package com.algoblock.structure.stack.method;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class Delete implements StructureMethod {
    private static final String PATTERN = "Stack(@).delete";
    
    @Override 
    public String getPattern() { 
        return PATTERN; 
    }
    
    @Override 
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        context.removeObject(FakeStack.TYPE_ID, objName);
    }
}