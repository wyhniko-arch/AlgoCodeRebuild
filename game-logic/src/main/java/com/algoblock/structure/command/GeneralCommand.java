package com.algoblock.structure.command;

import com.algoblock.context.RuntimeContext;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.MethodRegistryLoader;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * GeneralCommand：对所有结构通用的元指令集。
 *
 * 只有 Template 角色，没有 Instance（不创建游戏对象）。
 * 三条核心指令在构造阶段（registerStructures 时）就反射加载完毕，
 * 与 Stack/Queue 等结构的预设指令一致。
 *
 * 内置指令（pattern → 说明）：
 *   Command.clearBuffer    → 强制清空 RuntimeContext 缓冲区
 *   Command.in[@]          → 将 @ 所指的已注册 "in" 类指令设为 bufferCommandIn
 *   Command.out[@]         → 将 @ 所指的已注册 "out" 类指令设为 bufferCommandOut
 */
public class GeneralCommand extends Abstract {

    public static final String STRUCTURE_ID = "Command";

    public GeneralCommand() {
        super();
        // 从 methodRegistry.json 加载指令的 FQCN 映射（与 Stack/Queue 一致的流程）
        this.methodRegistry.putAll(MethodRegistryLoader.load(STRUCTURE_ID));

        // 声明阶段直接全部加载（不延迟到 initAllowedLimits 才动态拉取）
        ifLoadMethodDynamically("clearBuffer");
        ifLoadMethodDynamically("in");
        ifLoadMethodDynamically("out");
    }

    /** GeneralCommand 没有游戏对象实例，collectSnapshots 恒返回空列表。 */
    @Override
    public List<JsonObject> collectSnapshots(RuntimeContext context) {
        return new ArrayList<>();
    }
}