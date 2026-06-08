package com.algoblock.structure.stack;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.MethodRegistryLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FakeStack extends Abstract {

    public static final String TYPE_ID = "Stack";

    // ==========================================
    // Template 角色构造函数
    // 仅由 Logic.registerStructures() 调用一次
    // 负责反射加载所有指令，不持有任何游戏数据
    // ==========================================
    public FakeStack() {
        super();
        this.methodRegistry.putAll(MethodRegistryLoader.load(TYPE_ID));

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
    public String[] inspectAll(RuntimeContext context) {
        Set<String> names = context.getActiveObjectNames(TYPE_ID);
        List<String> result = new ArrayList<>();
        for (String name : names) {
            Instance obj = (Instance) context.getObject(TYPE_ID, name);
            if (obj != null) {
                for (String line : obj.inspect()) {
                    result.add(line);
                }
            }
        }
        return result.toArray(new String[0]);
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
        public String[] inspect() {
            List<String> lines = new ArrayList<>();
            lines.add("[Stack] name=" + name + " | top=" + top);
            if (top < 0) {
                lines.add("  (空栈)");
            } else {
                StringBuilder sb = new StringBuilder("  elements(bottom→top): [");
                for (int i = 0; i <= top; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(array[i]);
                }
                sb.append("]");
                lines.add(sb.toString());
            }
            return lines.toArray(new String[0]);
        }
    }
}