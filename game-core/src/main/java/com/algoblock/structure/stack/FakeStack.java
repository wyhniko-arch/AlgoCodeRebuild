package com.algoblock.structure.stack;

import com.algoblock.structure.Abstract;
import com.algoblock.structure.MethodRegistryLoader;

public class FakeStack extends Abstract {
    // 物理内存形态变量
    public int[] array;
    public int top; // 栈顶指针
    private static final int INITIAL_CAPACITY = 16;
    
    public static final String TYPE_ID = "Stack";

    public FakeStack() {
        super();
        this.array = new int[INITIAL_CAPACITY];
        this.top = -1;

        // 子类自主决定数据源，保护了 Abstract 的纯粹性
        this.methodRegistry.putAll(MethodRegistryLoader.load(TYPE_ID));

        // 默认注册的基础指令生命周期加载
        ifLoadMethodDynamically("init_full");
        ifLoadMethodDynamically("init_empty");
        ifLoadMethodDynamically("copy");
        ifLoadMethodDynamically("delete");
        ifLoadMethodDynamically("equal");
    }

    // 物理扩容工具
    public void resizeRawArray(int newCapacity) {
        int[] newArray = new int[newCapacity];
        System.arraycopy(array, 0, newArray, 0, array.length);
        this.array = newArray;
    }
}