package com.algoblock.structure.queue;

import com.algoblock.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.StructureMethod;

import java.util.HashMap;
import java.util.Map;

public class FakeQueue extends Abstract {
    // 数据结构的内部状态
    public int[] array; // 对同包下的Method类或Public可见，或提供getter/setter，此处设为public以保持示例简洁且解耦
    public int head;
    public int tail;
    public int size;
    private static final int INITIAL_CAPACITY = 16;
    
    public static final String TYPE_ID = "Queue";

    // 用于存放当前结构已经实例化并加载完毕的指令方法
    private final Map<String, StructureMethod> loadedMethods = new HashMap<>();

    // 模拟从 resource/Structure/Queue/methodRegistry.json 解析的反射映射表
    private final Map<String, String> methodRegistry = new HashMap<>();

    public FakeQueue() {
        this.array = new int[INITIAL_CAPACITY];
        this.head = 0;
        this.tail = 0;
        this.size = 0;

        // [硬编码模拟JSON解析] -> 实际开发中应在初始化时读取 methodRegistry.json
        methodRegistry.put("init_full", "com.algoblock.structure.queue.method.InitFull");
        methodRegistry.put("init_empty", "com.algoblock.structure.queue.method.InitEmpty");
        methodRegistry.put("copy", "com.algoblock.structure.queue.method.Copy");
        methodRegistry.put("delete", "com.algoblock.structure.queue.method.Delete");
        methodRegistry.put("equal", "com.algoblock.structure.queue.method.Equal");
        methodRegistry.put("pop", "com.algoblock.structure.queue.method.Pop");
        methodRegistry.put("add", "com.algoblock.structure.queue.method.Add");

        // 规范：加载Queue时，仅默认注册基础核心指令，pop和add不在此处预加载
        loadMethodDynamically("init_full");
        loadMethodDynamically("init_empty");
        loadMethodDynamically("copy");
        loadMethodDynamically("delete");
        loadMethodDynamically("equal");
    }

    // 暴露出的基础操作方法，供独立的Method类调用
    public void ensureCapacity() {
        if (size == array.length) {
            int[] newArray = new int[array.length * 2];
            for (int i = 0; i < size; i++) {
                newArray[i] = array[(head + i) % array.length];
            }
            array = newArray;
            head = 0;
            tail = size;
        }
    }

    public void enqueue(int val) {
        ensureCapacity();
        array[tail] = val;
        tail = (tail + 1) % array.length;
        size++;
    }

    public int dequeue() {
        if (size == 0) throw new IllegalStateException("Queue is empty");
        int val = array[head];
        head = (head + 1) % array.length;
        size--;
        return val;
    }

    @Override
    public boolean loadMethodDynamically(String instId) {
        // 如果该指令尚未注册，尝试根据 methodRegistry 加载
        if (!loadedMethods.containsKey(instId) && methodRegistry.containsKey(instId)) {
            String fqcn = methodRegistry.get(instId);
            try {
                // 利用反射获取具体的Method类并实例化
                StructureMethod methodInstance = (StructureMethod) Class.forName(fqcn).getDeclaredConstructor().newInstance();
                loadedMethods.put(instId, methodInstance);
                return true;
            } catch (Exception e) {
                System.err.println("[错误] Queue 指令加载失败: " + instId + "，目标类: " + fqcn);
                return false;
            }
        }
        // 若已加载或不存在该映射
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
        // [优化说明]: 此处 loadedMethods 是 HashMap，查找 instId 的时间复杂度为 O(1)
        StructureMethod method = loadedMethods.get(instId);
        if (method != null) {
            method.execute(args, runtimeContext);
        } else {
            System.err.println("[拦截] 未能在Queue中找到并执行指令: " + instId);
        }
    }
}