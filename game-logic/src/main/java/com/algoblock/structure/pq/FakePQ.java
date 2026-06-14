package com.algoblock.structure.pq;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.MethodRegistryLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * FakePQ：优先队列（二叉堆实现）。
 *
 * Template 角色：反射加载所有指令，挂载到引擎。
 * Instance 角色：纯数据对象，持有数组式完全二叉堆 + 两个配置开关。
 *
 * 可视化（inspectAsJson）：
 *   将堆按数组顺序输出为 nodes 列表，每个节点带 i / value / parent / left / right，
 *   前端可直接按 nodes[i] 索引画出二叉树结构。
 *
 * pop 只能弹堆顶（数组下标 0）—— 这是"可见的那个"，符合优先队列的可视化语义。
 */
public class FakePQ extends Abstract {

    public static final String STRUCTURE_ID = "PQ";

    public FakePQ() {
        super();
        this.methodRegistry.putAll(MethodRegistryLoader.load(STRUCTURE_ID));
        // 声明阶段加载默认指令；额外的（如 push/pop）走授权时动态拉取
        ifLoadMethodDynamically("initEmpty");
        ifLoadMethodDynamically("initFull");
        ifLoadMethodDynamically("initFullConfig");
        ifLoadMethodDynamically("delete");
        ifLoadMethodDynamically("copy");
        ifLoadMethodDynamically("equal");
    }

    @Override
    public List<JsonObject> collectSnapshots(RuntimeContext context) {
        Set<String> names = context.getActiveObjectNames(STRUCTURE_ID);
        List<JsonObject> result = new ArrayList<>();
        for (String name : names) {
            Instance obj = (Instance) context.getObject(STRUCTURE_ID, name);
            if (obj != null) {
                JsonObject snapshot = new JsonObject();
                snapshot.addProperty("structId", STRUCTURE_ID);
                snapshot.addProperty("name", name);
                snapshot.add("state", obj.inspectAsJson());
                result.add(snapshot);
            }
        }
        return result;
    }

    // ==========================================
    // Instance：纯数据对象（数组实现的二叉堆）
    // ==========================================

    public static class Instance extends Abstract.Instance {
        /** 堆数据，array[0..size-1] 有效。 */
        public int[] array;
        /** 当前元素数量。 */
        public int size;
        /** true=大根堆，false=小根堆。 */
        public boolean maxHeap;
        /** 是否允许重复元素插入。重复元素被拒时静默忽略。 */
        public boolean allowDuplicates;

        private static final int INITIAL_CAPACITY = 16;

        public Instance() {
            this.array           = new int[INITIAL_CAPACITY];
            this.size            = 0;
            this.maxHeap         = true;   // 默认大根堆
            this.allowDuplicates = true;   // 默认允许重复
        }

        // ---- 堆操作（业务逻辑） ----

        /** 比较：a 是否应该排在 b 之前（即"优先级更高"）。 */
        private boolean prior(int a, int b) {
            return maxHeap ? (a > b) : (a < b);
        }

        public void resizeIfFull() {
            if (size == array.length) {
                int[] na = new int[array.length * 2];
                System.arraycopy(array, 0, na, 0, array.length);
                array = na;
            }
        }

        /** 插入一个值；返回是否真正插入（重复元素在 allowDuplicates=false 时被拒）。 */
        public boolean offer(int value) {
            if (!allowDuplicates) {
                for (int i = 0; i < size; i++) {
                    if (array[i] == value) return false;
                }
            }
            resizeIfFull();
            array[size] = value;
            siftUp(size);
            size++;
            return true;
        }

        /** 弹出堆顶（最高优先级 / 数组下标 0）；空堆返回 null。 */
        public Integer poll() {
            if (size == 0) return null;
            int top = array[0];
            size--;
            if (size > 0) {
                array[0] = array[size];
                siftDown(0);
            }
            return top;
        }

        private void siftUp(int idx) {
            while (idx > 0) {
                int parent = (idx - 1) / 2;
                if (prior(array[idx], array[parent])) {
                    int tmp = array[idx]; array[idx] = array[parent]; array[parent] = tmp;
                    idx = parent;
                } else break;
            }
        }

        private void siftDown(int idx) {
            while (true) {
                int left  = idx * 2 + 1;
                int right = idx * 2 + 2;
                int best  = idx;
                if (left  < size && prior(array[left],  array[best])) best = left;
                if (right < size && prior(array[right], array[best])) best = right;
                if (best == idx) break;
                int tmp = array[idx]; array[idx] = array[best]; array[best] = tmp;
                idx = best;
            }
        }

        // ---- 可视化 ----

        @Override
        public JsonObject inspectAsJson() {
            JsonObject state = new JsonObject();
            state.addProperty("kind",            "PQ");
            state.addProperty("maxHeap",         maxHeap);
            state.addProperty("allowDuplicates", allowDuplicates);
            state.addProperty("size",            size);

            JsonArray nodes = new JsonArray();
            for (int i = 0; i < size; i++) {
                JsonObject n = new JsonObject();
                n.addProperty("i",     i);
                n.addProperty("value", array[i]);
                if (i == 0) n.add("parent", JsonNull.INSTANCE);
                else        n.addProperty("parent", (i - 1) / 2);
                int left  = i * 2 + 1;
                int right = i * 2 + 2;
                if (left  < size) n.addProperty("left",  left);  else n.add("left",  JsonNull.INSTANCE);
                if (right < size) n.addProperty("right", right); else n.add("right", JsonNull.INSTANCE);
                nodes.add(n);
            }
            state.add("nodes", nodes);
            return state;
        }
    }
}