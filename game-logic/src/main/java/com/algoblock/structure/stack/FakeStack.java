package com.algoblock.structure.stack;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.MethodRegistryLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class FakeStack extends Abstract {

    public static final String STRUCTURE_ID = "Stack";

    // ==========================================
    // Template 角色构造函数（反射加载，仅跑一次）
    // 仅由 Logic.registerStructures() 调用一次
    // 负责反射加载所有指令，不持有任何游戏数据
    // ==========================================
    public FakeStack() {
        super();
        this.methodRegistry.putAll(MethodRegistryLoader.load(STRUCTURE_ID));

        ifLoadMethodDynamically("init_full");
        ifLoadMethodDynamically("init_empty");
        ifLoadMethodDynamically("copy");
        ifLoadMethodDynamically("delete");
        ifLoadMethodDynamically("equal");
    }

    /**
     * Template 角色：汇报当前 context 中所有 Stack 游戏对象的状态
     */
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
    // Instance 角色：纯数据对象
    // 由 InitEmpty、InitFull、Copy 等指令在运行期创建
    // 绝不调用 FakeStack() 模板构造函数
    // ==========================================
    public static class Instance extends Abstract.Instance {
        public int[] array;
        public int top; // 栈顶指针，-1 表示空栈
        private static final int INITIAL_CAPACITY = 16;

        /**
         * 运行期数据对象构造函数：只初始化数据，无任何反射逻辑
         */
        public Instance() {
            this.array = new int[INITIAL_CAPACITY];
            this.top = -1;
        }

        /**
         * 物理扩容工具（数据对象自用）
         */
        public void resizeRawArray(int newCapacity) {
            int[] newArray = new int[newCapacity];
            System.arraycopy(array, 0, newArray, 0, array.length);
            this.array = newArray;
        }

        /**
         * 返回该 Stack 游戏对象的当前状态（非数据驱动，调试/展示用）
         */
        @Override
        public JsonObject inspectAsJson() {
            JsonObject state = new JsonObject();
            state.addProperty("top", top);
            JsonArray elements = new JsonArray();
            // bottom -> top 顺序
            for (int i = 0; i <= top; i++) {
                elements.add(array[i]);
            }
            state.add("elements", elements);
            return state;
        }
    }
}