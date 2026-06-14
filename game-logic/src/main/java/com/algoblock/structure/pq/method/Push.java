package com.algoblock.structure.pq.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.pq.FakePQ;

/**
 * PQ[@].push — 从缓冲区取一个值 offer 进堆。
 * tag=in：玩家执行时会先触发 bufferCommandOut（从其他结构 pop 出来一个值到 buffer）。
 */
public class Push implements StructureMethod {
    private static final String   PATTERN   = "PQ[@].push";
    private static final String[] ARG_HINTS = {"obj[PQ]"};
    private static final String[] TAGS      = {"in"};
    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        FakePQ.Instance obj = (FakePQ.Instance) context.getObject(FakePQ.STRUCTURE_ID, args[0]);
        if (obj == null) return;
        if (context.getandresetIsPlayerAction()) {
            context.triggerEngineCommand(context.getBufferCommandOut());
        }
        Integer val = context.popFromBuffer();
        if (val != null) {
            obj.offer(val);
            // 若 allowDuplicates=false 且元素已存在，offer 静默返回 false，
            // 不报错也不消耗任何东西——和 Stack 满栈、Queue 空队的行为风格一致。
        }
    }
}