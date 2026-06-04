package com.algoblock.structure.stack;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.StructureMethod;

import java.util.HashMap;
import java.util.Map;

public class FakeStack extends Abstract {
    public int[] array;
    public int top; // 栈顶指针
    private static final int INITIAL_CAPACITY = 16;
    
    public static final String TYPE_ID = "Stack";

    private final Map<String, StructureMethod> loadedMethods = new HashMap<>();
    private final Map<String, String> methodRegistry = new HashMap<>();

    public FakeStack() {
        this.array = new int[INITIAL_CAPACITY];
        this.top = -1;

        // 模拟解析 JSON
        methodRegistry.put("init_full", "com.algoblock.structure.stack.method.InitFull");
        methodRegistry.put("init_empty", "com.algoblock.structure.stack.method.InitEmpty");
        methodRegistry.put("copy", "com.algoblock.structure.stack.method.Copy");
        methodRegistry.put("delete", "com.algoblock.structure.stack.method.Delete");
        methodRegistry.put("equal", "com.algoblock.structure.stack.method.Equal");
        methodRegistry.put("pop", "com.algoblock.structure.stack.method.Pop");
        methodRegistry.put("push", "com.algoblock.structure.stack.method.Push");

        // 默认注册不消耗次数的基础指令
        loadMethodDynamically("init_full");
        loadMethodDynamically("init_empty");
        loadMethodDynamically("copy");
        loadMethodDynamically("delete");
        loadMethodDynamically("equal");
    }

    public void ensureCapacity() {
        if (top == array.length - 1) {
            int[] newArray = new int[array.length * 2];
            System.arraycopy(array, 0, newArray, 0, array.length);
            array = newArray;
        }
    }

    public void pushVal(int val) {
        ensureCapacity();
        array[++top] = val;
    }

    public int popVal() {
        if (top == -1) throw new IllegalStateException("Stack is empty");
        return array[top--];
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