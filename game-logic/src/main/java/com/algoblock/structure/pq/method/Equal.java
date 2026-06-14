package com.algoblock.structure.pq.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.pq.FakePQ;

/**
 * PQ.equal[@,@] — 判分用。
 *
 * 等值定义：size 相同 + 数组前 size 项逐位相等 + maxHeap/allowDuplicates 配置相同。
 * 注意这是"内部数组形态相等"，与"元素集合相等"不同——
 * 同一组元素在不同构造路径下可能得到不同的数组形态，但只要构造路径一致，结果就一致。
 */
public class Equal implements StructureMethod {
    private static final String   PATTERN   = "PQ.equal[@,@]";
    private static final String[] ARG_HINTS = {"obj[PQ]", "obj[PQ]"};
    private static final String[] TAGS      = {};
    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        FakePQ.Instance a = (FakePQ.Instance) context.getObject(FakePQ.STRUCTURE_ID, args[0]);
        FakePQ.Instance b = (FakePQ.Instance) context.getObject(FakePQ.STRUCTURE_ID, args[1]);
        context.incrementRunCheck();
        if (a == null || b == null) return;
        if (a.size != b.size) return;
        if (a.maxHeap != b.maxHeap) return;
        if (a.allowDuplicates != b.allowDuplicates) return;
        for (int i = 0; i < a.size; i++) {
            if (a.array[i] != b.array[i]) return;
        }
        context.incrementPassedCheck();
    }
}