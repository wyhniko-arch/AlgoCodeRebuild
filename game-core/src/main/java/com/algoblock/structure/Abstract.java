package com.algoblock.structure;

import java.util.HashMap;
import java.util.Map;

import com.algoblock.context.RuntimeContext;

public abstract class Abstract {
    public String name;
    
    // 提至基类的通用缓存与注册表。使用 protected final 允许子类初始化写入，但不可被替换对象引用。
    protected final Map<String, StructureMethod> loadedMethods = new HashMap<>();
    protected final Map<String, String> methodRegistry = new HashMap<>();

    // 必须保留的无参构造，防止波及未重构的其他结构衍生类
    public Abstract() {}

    /**
     * 获取当前结构已加载的所有指令的模板映射
     * @return 映射表 (Instruction ID -> Pattern)
     */
    public Map<String, String> getPatterns() {
        Map<String, String> patterns = new HashMap<>();
        for (Map.Entry<String, StructureMethod> entry : loadedMethods.entrySet()) {
            patterns.put(entry.getKey(), entry.getValue().getPattern());
        }
        return patterns;
    }

    /**
     * 结构端分发器：引擎将提取好的参数传递给结构
     */
    public void executeInstruction(String instId, String[] args, RuntimeContext context) {
        StructureMethod method = loadedMethods.get(instId);
        if (method != null) {
            method.execute(args, context);
        } else {
            // 动态获取子类类名，保留精确日志
            System.err.println("[拦截] 未能在 " + this.getClass().getSimpleName() + " 中找到并执行指令: " + instId);
        }
    }

    public boolean loadMethodDynamically(String instId) {
        if (!loadedMethods.containsKey(instId) && methodRegistry.containsKey(instId)) {
            String fqcn = methodRegistry.get(instId);
            try {
                StructureMethod methodInstance = (StructureMethod) Class.forName(fqcn).getDeclaredConstructor().newInstance();
                loadedMethods.put(instId, methodInstance);
                return true;
            } catch (Exception e) {
                // 精确捕捉目标类全限定名(fqcn)，保留排错细节
                System.err.println("[错误] " + this.getClass().getSimpleName() + " 指令加载失败: " + instId + "，目标类: " + fqcn);
                return false;
            }
        }
        return loadedMethods.containsKey(instId);
    }
}