package com.algoblock.structure.command;

import com.algoblock.structure.Abstract;
import com.algoblock.structure.MethodRegistryLoader;
import com.algoblock.context.RuntimeContext;
import com.google.gson.JsonObject;
 
import java.util.ArrayList;
import java.util.List;
 
/**
 * GeneralCommand：对所有结构通用的元指令集。
 *
 * 该结构只有 Template 角色，没有 Instance（不创建游戏对象）。
 * 内置方法通过 methodRegistry 的 JSON 注册，也可在构造时直接加载。
 *
 * 内置指令（pattern → 说明）：
 *   Command.ClearBuffer          → 强制清空 RuntimeContext 缓冲区
 *   Command.In(@)                → 将 @ 所指的已注册 "in" 类指令设为 bufferCommandIn
 *   Command.Out(@)               → 将 @ 所指的已注册 "out" 类指令设为 bufferCommandOut
 */
public class GeneralCommand extends Abstract {
 
    public static final String STRUCTURE_ID = "Command";
 
    public GeneralCommand() {
        super();
        // 优先从 methodRegistry.json 加载（若存在），再内联注册三条核心方法
        this.methodRegistry.putAll(MethodRegistryLoader.load(STRUCTURE_ID));
        registerBuiltins();
    }
 
    private void registerBuiltins() {
        // ClearBuffer
        if (!loadedMethods.containsKey("clear_buffer")) {
            loadedMethods.put("clear_buffer", new ClearBufferMethod());
        }
        // In
        if (!loadedMethods.containsKey("in")) {
            loadedMethods.put("in", new InMethod());
        }
        // Out
        if (!loadedMethods.containsKey("out")) {
            loadedMethods.put("out", new OutMethod());
        }
    }
 
    /** GeneralCommand 没有游戏对象实例，collectSnapshots 恒返回空列表。 */
    @Override
    public List<JsonObject> collectSnapshots(RuntimeContext context) {
        return new ArrayList<>();
    }
 
    // ==========================================
    // 内联方法实现（避免额外文件）
    // ==========================================
 
    /** Command.ClearBuffer */
    private static class ClearBufferMethod implements com.algoblock.structure.StructureMethod {
        @Override public String   getPattern()  { return "Command.ClearBuffer"; }
        @Override public String[] getArgHints() { return new String[0]; }
        @Override public String[] getTags()     { return new String[0]; }
        @Override public void execute(String[] args, RuntimeContext context) {
            context.clearBuffer();
            com.algoblock.tools.buffer.RowBuffer.append("[Command] ClearBuffer 执行，缓冲区已清空");
        }
    }
 
    /**
     * Command.In(@)
     * @ 语义：cmd[in] — 必须是已注册且带 "in" 标签的指令的完整展开
     */
    private static class InMethod implements com.algoblock.structure.StructureMethod {
        @Override public String   getPattern()  { return "Command.In(@)"; }
        @Override public String[] getArgHints() { return new String[]{"cmd[in]"}; }
        @Override public String[] getTags()     { return new String[0]; }
        @Override public void execute(String[] args, RuntimeContext context) {
            String newCommandIn = args[0];
            context.setBufferCommandIn(newCommandIn);
            com.algoblock.tools.buffer.RowBuffer.append("[Command] bufferCommandIn 已更新为: " + newCommandIn);
        }
    }
 
    /**
     * Command.Out(@)
     * @ 语义：cmd[out] — 必须是已注册且带 "out" 标签的指令的完整展开
     */
    private static class OutMethod implements com.algoblock.structure.StructureMethod {
        @Override public String   getPattern()  { return "Command.Out(@)"; }
        @Override public String[] getArgHints() { return new String[]{"cmd[out]"}; }
        @Override public String[] getTags()     { return new String[0]; }
        @Override public void execute(String[] args, RuntimeContext context) {
            String newCommandOut = args[0];
            context.setBufferCommandOut(newCommandOut);
            com.algoblock.tools.buffer.RowBuffer.append("[Command] bufferCommandOut 已更新为: " + newCommandOut);
        }
    }
}