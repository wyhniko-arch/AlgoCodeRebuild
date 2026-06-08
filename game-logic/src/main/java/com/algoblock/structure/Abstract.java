package com.algoblock.structure;

import java.util.HashMap;
import java.util.Map;

import com.algoblock.context.RuntimeContext;
import com.algoblock.tools.buffer.RowBuffer;

public abstract class Abstract {

    // ==========================================
    // Template 角色专属：指令注册表与已加载方法
    // 仅由 Logic.registerStructures() 创建的模板实例持有并使用
    // 游戏运行期通过指令创建的数据对象（Instance）绝不走此路径
    // ==========================================
    protected final Map<String, StructureMethod> loadedMethods = new HashMap<>();
    protected final Map<String, String> methodRegistry = new HashMap<>();

    public Abstract() {}

    /**
     * 获取当前结构已加载的所有指令的模板映射（Template 角色使用）
     */
    public Map<String, String> getPatterns() {
        Map<String, String> patterns = new HashMap<>();
        for (Map.Entry<String, StructureMethod> entry : loadedMethods.entrySet()) {
            patterns.put(entry.getKey(), entry.getValue().getPattern());
        }
        return patterns;
    }

    /**
     * 结构端分发器：引擎将提取好的参数传递给结构（Template 角色使用）
     */
    public void executeCommand(String commandId, String[] args, RuntimeContext context) {
        StructureMethod method = loadedMethods.get(commandId);
        if (method != null) {
            method.execute(args, context);
        } else {
            RowBuffer.append("[拦截] 未能在 " + this.getClass().getSimpleName() + " 中找到并执行指令: " + commandId);
        }
    }

    /**
     * 动态加载指令方法（Template 角色使用，仅在注册阶段调用）
     */
    public boolean ifLoadMethodDynamically(String commandId) {
        if (!loadedMethods.containsKey(commandId) && methodRegistry.containsKey(commandId)) {
            String fqcn = methodRegistry.get(commandId);
            try {
                StructureMethod methodInstance = (StructureMethod) Class.forName(fqcn).getDeclaredConstructor().newInstance();
                loadedMethods.put(commandId, methodInstance);
                return true;
            } catch (Exception e) {
                RowBuffer.append("[错误] " + this.getClass().getSimpleName() + " 指令加载失败: " + commandId + "，目标类: " + fqcn);
                return false;
            }
        }
        return loadedMethods.containsKey(commandId);
    }

    // ==========================================
    // Instance 角色：数据对象身份标识
    // 运行期由指令创建的数据对象继承此内部类
    // ==========================================

    /**
     * 所有游戏数据对象的基类。
     * 只持有数据，不持有任何指令/反射逻辑。
     * 子结构（FakeStack、FakeQueue 等）将自己的 Instance 内部类继承此类。
     */
    public static abstract class Instance {
        public String name;

        /**
         * 返回该游戏对象的当前状态信息（非数据驱动，调试/展示用）。
         * 每个具体 Instance 子类自行实现，描述自身数据内容。
         * 格式建议：第一行为对象标识，后续行为具体状态。
         */
        public abstract String[] inspect();
    }

    // ==========================================
    // Template 角色：对外暴露 inspect 入口
    // 由 RuntimeContext.inspectAll() 调用，汇报本类型下所有活着的游戏对象状态
    // ==========================================

    /**
     * 汇报本结构类型下，当前 context 中所有活着游戏对象的状态。
     * 子结构的 Template 类（FakeStack、FakeQueue 等）需要重写此方法。
     */
    public abstract String[] inspectAll(RuntimeContext context);
}