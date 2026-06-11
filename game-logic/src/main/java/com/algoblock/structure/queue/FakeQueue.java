package com.algoblock.structure.queue;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.MethodRegistryLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class FakeQueue extends Abstract {

    public static final String TYPE_ID = "Queue";

    // ==========================================
    // Template 角色构造函数（反射加载，仅跑一次）
    // 仅由 Logic.registerStructures() 调用一次
    // 负责反射加载所有指令，不持有任何游戏数据
    // ==========================================
    public FakeQueue() {
        super();
        this.methodRegistry.putAll(MethodRegistryLoader.load(TYPE_ID));

        ifLoadMethodDynamically("init_full");
        ifLoadMethodDynamically("init_empty");
        ifLoadMethodDynamically("copy");
        ifLoadMethodDynamically("delete");
        ifLoadMethodDynamically("equal");
    }

    /**
     * Template 角色：汇报当前 context 中所有 Queue 游戏对象的状态
     */
    @Override
    public List<JsonObject> collectSnapshots(RuntimeContext context) {
        Set<String> names = context.getActiveObjectNames(TYPE_ID);
        List<JsonObject> result = new ArrayList<>();
        for (String name : names) {
            Instance obj = (Instance) context.getObject(TYPE_ID, name);
            if (obj != null) {
                JsonObject snapshot = new JsonObject();
                snapshot.addProperty("structId", TYPE_ID);
                snapshot.addProperty("name", name);
                snapshot.add("state", obj.inspectAsJson());
                result.add(snapshot);
            }
        }
        return result;
    }

    // ==========================================
    // Instance 角色：纯数据对象（循环队列）
    // 由 InitEmpty、InitFull、Copy 等指令在运行期创建
    // 绝不调用 FakeQueue() 模板构造函数
    // ==========================================
    public static class Instance extends Abstract.Instance {
        public int[] array;
        public int head;
        public int tail;
        public int size;
        private static final int INITIAL_CAPACITY = 16;

        /**
         * 运行期数据对象构造函数：只初始化数据，无任何反射逻辑
         */
        public Instance() {
            this.array = new int[INITIAL_CAPACITY];
            this.head = 0;
            this.tail = 0;
            this.size = 0;
        }

        /**
         * 物理扩容工具（含解环逻辑，数据对象自用）
         */
        public void resizeRawArray(int newCapacity) {
            int[] newArray = new int[newCapacity];
            for (int i = 0; i < size; i++) {
                newArray[i] = array[(head + i) % array.length];
            }
            this.array = newArray;
            this.head = 0;
            this.tail = size;
        }

        /**
         * 返回该 Queue 游戏对象的当前状态（非数据驱动，调试/展示用）
         */
        @Override
        public JsonObject inspectAsJson() {
            JsonObject state = new JsonObject();
            state.addProperty("size", size);
            state.addProperty("head", head);
            state.addProperty("tail", tail);
            JsonArray elements = new JsonArray();
            // head → tail 逻辑顺序
            for (int i = 0; i < size; i++) {
                elements.add(array[(head + i) % array.length]);
            }
            state.add("elements", elements);
            return state;
        }
    }
}