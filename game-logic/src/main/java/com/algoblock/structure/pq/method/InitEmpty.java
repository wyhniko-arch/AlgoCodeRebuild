package com.algoblock.structure.pq.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.pq.FakePQ;

/** PQ[@] — 用默认配置（大根堆、允许重复）创建空优先队列。 */
public class InitEmpty implements StructureMethod {
    private static final String   PATTERN   = "PQ[@]";
    private static final String[] ARG_HINTS = {"any"};
    private static final String[] TAGS      = {};
    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        FakePQ.Instance obj = new FakePQ.Instance();
        obj.name = args[0];
        context.putObject(FakePQ.STRUCTURE_ID, obj.name, obj);
    }
}