package com.algoblock.structure.stack.method;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class InitFull implements StructureMethod {
    private static final String PATTERN = "Stack(@,(@))";
    @Override public String getPattern() { return PATTERN; }
    @Override public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        String values = args[1];
        FakeStack newObj = new FakeStack();
        newObj.name = objName;
        if (!values.isEmpty()) {
            // 字符串解析，依次压栈（底层对应栈底，最新压入的对应栈顶）
            for (String v : values.split(",")) {
                newObj.pushVal(Integer.parseInt(v));
            }
        }
        context.putObject(FakeStack.TYPE_ID, objName, newObj);
    }
}