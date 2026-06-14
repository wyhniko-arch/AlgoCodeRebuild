package com.algoblock.structure.pq.method;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.StructureMethod;
import com.algoblock.structure.pq.FakePQ;

/**
 * PQ[@,[@],[@],[@]] — 名称 + 数值列表 + maxHeap(true/false) + allowDuplicates(true/false)
 *
 * 例：PQ[(P),[(3,1,4,1,5)],[(false)],[(false)]] → 小根堆、去重
 *   构造完成后 array=[1,4,3,5]（4 被 1 顶下，第二个 1 因去重被拒）
 */
public class InitFullConfig implements StructureMethod {
    private static final String   PATTERN   = "PQ[@,[@],[@],[@]]";
    private static final String[] ARG_HINTS = {"any", "any", "any", "any"};
    private static final String[] TAGS      = {};
    @Override public String   getPattern()  { return PATTERN; }
    @Override public String[] getArgHints() { return ARG_HINTS; }
    @Override public String[] getTags()     { return TAGS; }
    @Override
    public void execute(String[] args, RuntimeContext context) {
        FakePQ.Instance obj = new FakePQ.Instance();
        obj.name            = args[0];
        obj.maxHeap         = Boolean.parseBoolean(args[2].trim());
        obj.allowDuplicates = Boolean.parseBoolean(args[3].trim());
        String values = args[1];
        if (!values.isEmpty()) {
            for (String v : values.split(",")) {
                obj.offer(Integer.parseInt(v.trim()));
            }
        }
        context.putObject(FakePQ.STRUCTURE_ID, obj.name, obj);
    }
}