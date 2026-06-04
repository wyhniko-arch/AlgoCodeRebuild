package com.algoblock.structure.queue;

import com.algoblock.structure.Abstract;
import com.algoblock.structure.MethodRegistryLoader;

public class FakeQueue extends Abstract {
    // 物理内存形态变量
    public int[] array;
    public int head;
    public int tail;
    public int size;
    private static final int INITIAL_CAPACITY = 16;
    
    public static final String TYPE_ID = "Queue";

    public FakeQueue() {
        super();
        this.array = new int[INITIAL_CAPACITY];
        this.head = 0;
        this.tail = 0;
        this.size = 0;

        // 子类自主决定数据源
        this.methodRegistry.putAll(MethodRegistryLoader.load(TYPE_ID));

        // 默认注册的基础指令
        loadMethodDynamically("init_full");
        loadMethodDynamically("init_empty");
        loadMethodDynamically("copy");
        loadMethodDynamically("delete");
        loadMethodDynamically("equal");
    }

    // 物理扩容工具（包含解环逻辑）
    public void resizeRawArray(int newCapacity) {
        int[] newArray = new int[newCapacity];
        for (int i = 0; i < size; i++) {
            newArray[i] = array[(head + i) % array.length];
        }
        this.array = newArray;
        this.head = 0;
        this.tail = size;
    }
}