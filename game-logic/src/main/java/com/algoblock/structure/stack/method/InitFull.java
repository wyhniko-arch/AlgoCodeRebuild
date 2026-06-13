package com.algoblock.structure.stack.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.stack.FakeStack;

public class InitFull implements StructureMethod {
    private static final String   PATTERN   = "Stack[@,[@]]";
    private static final String[] ARG_HINTS = {"any", "any"};
    private static final String[] TAGS      = {};
    @Override public String getPattern()    { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override 
    public void execute(String[] args, RuntimeContext context) {
        String objName = args[0];
        String values = args[1];
        FakeStack.Instance newObj = new FakeStack.Instance();
        newObj.name = objName;
        
        if (!values.isEmpty()) {
            // [可变变量（业务逻辑）]: 批量初始化写入。直接操作物理栈顶指针完成数据装载
            for (String v : values.split(",")) {
                if (newObj.top == newObj.array.length - 1) {
                    newObj.resizeRawArray(newObj.array.length * 2);
                }
                newObj.array[++newObj.top] = Integer.parseInt(v);
            }
        }
        context.putObject(FakeStack.STRUCTURE_ID, objName, newObj);
    }
}