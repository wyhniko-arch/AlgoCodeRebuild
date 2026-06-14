package com.algoblock.structure.pq.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.pq.FakePQ;

public class Copy implements StructureMethod {
    private static final String   PATTERN   = "PQ[@].copy[@]";
    private static final String[] ARG_HINTS = {"obj[PQ]", "any"};
    private static final String[] TAGS      = {};
    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        FakePQ.Instance src = (FakePQ.Instance) context.getObject(FakePQ.STRUCTURE_ID, args[0]);
        if (src == null) return;
        FakePQ.Instance dest = new FakePQ.Instance();
        dest.name            = args[1];
        dest.array           = new int[src.array.length];
        System.arraycopy(src.array, 0, dest.array, 0, src.array.length);
        dest.size            = src.size;
        dest.maxHeap         = src.maxHeap;
        dest.allowDuplicates = src.allowDuplicates;
        context.putObject(FakePQ.STRUCTURE_ID, dest.name, dest);
    }
}