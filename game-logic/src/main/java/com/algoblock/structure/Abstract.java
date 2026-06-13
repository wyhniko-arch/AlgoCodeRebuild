package com.algoblock.structure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.algoblock.context.RuntimeContext;
import com.algoblock.tools.buffer.RowBuffer;

import com.google.gson.JsonObject;

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
     * 获取已加载的所有 StructureMethod（供 Logic 注册时读取 argHints / tags）。
     */
    public Map<String, StructureMethod> getLoadedMethods() {
        return loadedMethods;
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
    /** 通过 methodRegistry（FQCN）动态加载。 */
    public boolean ifLoadMethodDynamically(String commandId) {
        if (loadedMethods.containsKey(commandId)) return true;
        String fqcn = methodRegistry.get(commandId);
        if (fqcn == null) return false;
        try {
            StructureMethod m = (StructureMethod) Class.forName(fqcn)
                    .getDeclaredConstructor().newInstance();
            loadedMethods.put(commandId, m);
            return true;
        } catch (Exception e) {
            RowBuffer.append("[错误] " + this.getClass().getSimpleName()
                    + " 指令加载失败: " + commandId + "，目标类: " + fqcn);
            return false;
        }
    }
 

    // ==========================================
    // Instance 基类
    // ==========================================

    /**
     * 所有游戏数据对象的基类。
     * 只持有数据，不持有任何指令/反射逻辑。
     * 子结构（FakeStack、FakeQueue 等）将自己的 Instance 内部类继承此类。
     */
    public static abstract class Instance {
        public String name;

        /**
         * 以结构化 JSON 形式返回本对象的当前状态（非数据驱动，调试/展示用）。
         * 返回的 JsonObject 只包含状态字段本身，例如：
         * {"top":2,"elements":[1,3,4]}
         * structId 和 name 由 Template 的 collectSnapshots() 统一包装，不在此处填写
         */
        public abstract JsonObject inspectAsJson();
    }

    // ==========================================
    // Template 角色：收集本类型所有活着的对象的快照
    // 由 RuntimeContext.collectAllSnapshots() 调用
    // ==========================================

    /**
     * 遍历 context 中属于本结构类型的所有 Instance,
     * 返回快照列表，每个快照格式：
     * {
     *     "structId":"Stack",
     *     "name":"B",
     *     "state": { ... } //这里面是 inspectAsJson() 的结果
     * }
     */
    public abstract List<JsonObject> collectSnapshots(RuntimeContext context);
}