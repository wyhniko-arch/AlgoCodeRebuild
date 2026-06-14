package com.algoblock.structure.pq.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.pq.FakePQ;

public class Delete implements StructureMethod {
    private static final String   PATTERN   = "PQ[@].delete";
    private static final String[] ARG_HINTS = {"obj[PQ]"};
    private static final String[] TAGS      = {};
    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        context.removeObject(FakePQ.STRUCTURE_ID, args[0]);
    }
}