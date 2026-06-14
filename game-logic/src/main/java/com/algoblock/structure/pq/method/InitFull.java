package com.algoblock.structure.pq.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.pq.FakePQ;

/**
 * PQ[@,[@]] — 名称 + 逗号分隔的初始值列表。
 * 使用默认配置（大根堆、允许重复），把值依次 offer 进去（自动建堆）。
 */
public class InitFull implements StructureMethod {
    private static final String   PATTERN   = "PQ[@,[@]]";
    private static final String[] ARG_HINTS = {"any", "any"};
    private static final String[] TAGS      = {};
    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        FakePQ.Instance obj = new FakePQ.Instance();
        obj.name = args[0];
        String values = args[1];
        if (!values.isEmpty()) {
            for (String v : values.split(",")) {
                obj.offer(Integer.parseInt(v.trim()));
            }
        }
        context.putObject(FakePQ.STRUCTURE_ID, obj.name, obj);
    }
}