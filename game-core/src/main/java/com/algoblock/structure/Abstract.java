package com.algoblock.structure;

import com.algoblock.RuntimeContext;
import java.util.Map;

public abstract class Abstract {
    public String name;
    public Abstract() {}

    /**
     * 获取当前结构已加载的所有指令的模板映射
     * @return 映射表 (Instruction ID -> Pattern)
     */
    public abstract Map<String, String> getPatterns();

    /**
     * 结构端分发器：引擎将提取好的参数传递给结构
     */
    public abstract void executeInstruction(String instId, String[] args, RuntimeContext context);

    public abstract boolean loadMethodDynamically(String instId);
}