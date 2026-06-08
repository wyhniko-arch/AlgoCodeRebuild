package com.algoblock.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.algoblock.logic.Logic;
import com.algoblock.structure.Abstract;
import com.algoblock.tools.buffer.RowBuffer;

public class RuntimeContext {
    private final Logic core;
    public RuntimeContext(Logic core) { this.core = core; }

    // 存储运行期游戏数据对象（Instance），key = "TypeId_name"
    // 与 Template 完全隔离：Map 的值类型改为 Abstract.Instance
    private final Map<String, Abstract.Instance> objects = new HashMap<>();

    private final Queue<Integer> buffer = new LinkedList<>();

    private boolean isPlayerAction;
    private String bufferCommandIn;
    private String bufferCommandOut;

    private int runCheckCount = 0;
    private int passedCheckCount = 0;

    private String generateKey(String struct, String name) { return struct + "_" + name; }

    // ==========================================
    // 游戏对象（Instance）的增删查
    // ==========================================

    public void putObject(String struct, String name, Abstract.Instance obj) {
        RowBuffer.append("[Debug] [Context] 注册游戏对象: " + struct + " -> " + name);
        objects.put(generateKey(struct, name), obj);
    }

    public Abstract.Instance getObject(String struct, String name) {
        return objects.get(generateKey(struct, name));
    }

    public void removeObject(String struct, String name) {
        RowBuffer.append("[Debug] [Context] 抹除游戏对象: " + struct + " -> " + name);
        objects.remove(generateKey(struct, name));
    }

    /**
     * 基于结构 ID 严格过滤命名空间，返回该类型下所有活着对象的名称集合。
     * Queue 的指令只能联想出 Queue 的对象。
     */
    public Set<String> getActiveObjectNames(String structId) {
        Set<String> names = new HashSet<>();
        for (String key : objects.keySet()) {
            String[] parts = key.split("_", 2);
            if (parts.length == 2 && parts[0].equals(structId)) {
                names.add(parts[1]);
            }
        }
        return names;
    }

    // ==========================================
    // 全局状态 inspect（非数据驱动，调试/展示用）
    // ==========================================

    /**
     * 遍历当前所有活着的游戏对象，逐一调用其 inspect() 方法，汇总返回。
     * 此方法不依赖关卡配置，不受指令权限控制，纯粹用于调试和展示。
     *
     * @param templateMap 由 Logic 传入的 structId -> Template 映射，
     *                    用于找到每个类型对应的 inspectAll 实现。
     *                    （Template 实例只有 Logic 持有，Context 不持有）
     */
    public String[] inspectAll(Map<String, Abstract> templateMap) {
        List<String> result = new ArrayList<>();
        // 按已注册的 Template 类型逐类汇报
        for (Map.Entry<String, Abstract> entry : templateMap.entrySet()) {
            String[] lines = entry.getValue().inspectAll(this);
            for (String line : lines) {
                result.add(line);
            }
        }
        return result.toArray(new String[0]);
    }

    // ==========================================
    // 其余原有方法（完全保持不变）
    // ==========================================

    public void setBufferConfig(String commandIn, String commandOut) {
        this.bufferCommandIn = commandIn;
        this.bufferCommandOut = commandOut;
    }

    public void pushToBuffer(int value) { buffer.offer(value); }
    public Integer popFromBuffer() { return buffer.poll(); }
    public void clearBuffer() { buffer.clear(); }
    public String getBufferCommandIn() { return bufferCommandIn; }
    public String getBufferCommandOut() { return bufferCommandOut; }
    public void resetCheckCounts() { this.runCheckCount = 0; this.passedCheckCount = 0; }
    public void incrementRunCheck() { this.runCheckCount++; }
    public void incrementPassedCheck() { this.passedCheckCount++; }
    public boolean isWinConditionMet() { return runCheckCount > 0 && runCheckCount == passedCheckCount; }
    public void triggerEngineCommand(String statement) { core.triggerEngineCommand(statement); }
    public void setIsPlayerAction(boolean isPlayerAction) { this.isPlayerAction = isPlayerAction; }
    public boolean getandresetIsPlayerAction() { boolean value = this.isPlayerAction; this.isPlayerAction = false; return value; }
}