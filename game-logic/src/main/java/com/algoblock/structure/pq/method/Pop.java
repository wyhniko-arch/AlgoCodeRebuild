package com.algoblock.structure.pq.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.pq.FakePQ;

/**
 * PQ[@].pop — 弹出堆顶（"可见的那个"）推入缓冲区。
 * tag=out：玩家执行后触发 bufferCommandIn。
 */
public class Pop implements StructureMethod {
    private static final String   PATTERN   = "PQ[@].pop";
    private static final String[] ARG_HINTS = {"obj[PQ]"};
    private static final String[] TAGS      = {"out"};
    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        FakePQ.Instance obj = (FakePQ.Instance) context.getObject(FakePQ.STRUCTURE_ID, args[0]);
        if (obj == null) return;
        Integer val = obj.poll();
        if (val != null) context.pushToBuffer(val);
        if (context.getandresetIsPlayerAction()) {
            context.triggerEngineCommand(context.getBufferCommandIn());
        }
    }
}