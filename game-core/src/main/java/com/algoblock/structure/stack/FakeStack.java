package com.algoblock.structure.stack;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.MethodRegistryLoader;
import com.algoblock.structure.StructureMethod;

import java.util.HashMap;
import java.util.Map;

public class FakeStack extends Abstract {
    // [规范化约束（不可更改）]: 状态容器。定义数据结构在内存中的物理布局，作为外部策略类操作的标准凭证。
    public int[] array;
    public int top; // 栈顶指针
    private static final int INITIAL_CAPACITY = 16;
    
    public static final String TYPE_ID = "Stack";

    // [规范化约束（不可更改）]: 运行时缓存，存储已初始化的指令策略实例，避免重复反射带来的性能开销
    private final Map<String, StructureMethod> loadedMethods = new HashMap<>();

    // [规范化约束（不可更改）]: 元数据注册表，维护指令标识符(instId)与具体策略类全限定名(FQCN)的映射关系
    private final Map<String, String> methodRegistry = new HashMap<>();

    public FakeStack() {
        this.array = new int[INITIAL_CAPACITY];
        this.top = -1;

        // 从 JSON 加载方法注册表
        methodRegistry.putAll(MethodRegistryLoader.load(TYPE_ID));

        // 默认注册的基础指令生命周期加载
        loadMethodDynamically("init_full");
        loadMethodDynamically("init_empty");
        loadMethodDynamically("copy");
        loadMethodDynamically("delete");
        loadMethodDynamically("equal");
    }

    // [规范化约束（不可更改）]: 纯底层的内存再分配工具，仅执行数组拷贝，不涉及特定业务逻辑校验
    public void resizeRawArray(int newCapacity) {
        int[] newArray = new int[newCapacity];
        System.arraycopy(array, 0, newArray, 0, array.length);
        this.array = newArray;
    }

    @Override
    public boolean loadMethodDynamically(String instId) {
        if (!loadedMethods.containsKey(instId) && methodRegistry.containsKey(instId)) {
            String fqcn = methodRegistry.get(instId);
            try {
                StructureMethod methodInstance = (StructureMethod) Class.forName(fqcn).getDeclaredConstructor().newInstance();
                loadedMethods.put(instId, methodInstance);
                return true;
            } catch (Exception e) {
                System.err.println("[错误] Stack 指令加载失败: " + instId);
                return false;
            }
        }
        return loadedMethods.containsKey(instId);
    }

    @Override
    public Map<String, String> getPatterns() {
        Map<String, String> patterns = new HashMap<>();
        for (Map.Entry<String, StructureMethod> entry : loadedMethods.entrySet()) {
            patterns.put(entry.getKey(), entry.getValue().getPattern());
        }
        return patterns;
    }

    @Override
    public void executeInstruction(String instId, String[] args, RuntimeContext runtimeContext) {
        StructureMethod method = loadedMethods.get(instId);
        if (method != null) {
            method.execute(args, runtimeContext);
        } else {
            System.err.println("[拦截] 未能在Stack中找到并执行指令: " + instId);
        }
    }
}