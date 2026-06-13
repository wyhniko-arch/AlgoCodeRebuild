package com.algoblock.context;

import java.util.ArrayList;
import java.util.Collections;
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

import com.google.gson.JsonObject;

public class RuntimeContext {

    private final Logic core;
    public RuntimeContext(Logic core) { this.core = core; }

    // 主存储，运行期游戏数据对象，key = "StructId_name"
    // 与 Template 完全隔离：Map 的值类型改为 Abstract.Instance
    private final Map<String, Abstract.Instance> objects = new HashMap<>();
    
    /**
     * 快速索引：structId → 该类型下所有存活对象的名称集合。
     * putObject / removeObject 时同步维护，词法分析阶段用 O(1) 取出候选名称集。
     */
    private final Map<String, Set<String>> objectNameIndex = new HashMap<>();

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
        objectNameIndex.computeIfAbsent(struct, k -> new HashSet<>()).add(name);
    }

    public Abstract.Instance getObject(String struct, String name) {
        return objects.get(generateKey(struct, name));
    }

    public void removeObject(String struct, String name) {
        RowBuffer.append("[Debug] [Context] 抹除游戏对象: " + struct + " -> " + name);
        objects.remove(generateKey(struct, name));
        Set<String> names = objectNameIndex.get(struct);
        if (names != null) {
            names.remove(name);
            if (names.isEmpty()) objectNameIndex.remove(struct);
        }
    }

    /**
     * O(1) 取出指定结构类型下所有存活对象的名称集合（只读视图）。
     * 词法分析 obj[...] 语义展开时调用。
     */
    public Set<String> getActiveObjectNames(String structId) {
        Set<String> names = objectNameIndex.get(structId);
        return (names != null) ? Collections.unmodifiableSet(names) : Collections.emptySet();
    }
    /** 返回所有结构下所有存活对象的名称（obj 不带方括号时使用，先这么写吧，后面再改）。*/
    public Set<String> getAllActiveObjectNames() {
        Set<String> all = new HashSet<>();
        for (Set<String> names : objectNameIndex.values()) all.addAll(names);
        return Collections.unmodifiableSet(all);
    }
 
    // ==========================================
    // 全局状态 inspect（非数据驱动，用于返回前端需要的结构格式）
    // query:objects
    // ==========================================

    /**
     * 遍历templateMap中每个Template，收集所有活着游戏对象的JSON快照，汇总返回。
     * 每个快照格式：{"structId":"Stack","name":"B","state":{...}}
     * 此方法不依赖关卡配置，不受指令权限控制，纯粹用于调试和展示。
     *
     * @param templateMap Logic 持有的 structId -> Template 映射
     */
    public List<JsonObject> collectAllSnapshots(Map<String, Abstract> templateMap) {
        List<JsonObject> result = new ArrayList<>();
        for (Abstract template: templateMap.values()) {
            result.addAll(template.collectSnapshots(this));
        }
        return result;
    }

    // ==========================================
    // 其余原有方法
    // ==========================================

    public void setBufferConfig(String commandIn, String commandOut) {
        this.bufferCommandIn = commandIn;
        this.bufferCommandOut = commandOut;
    }
    public void setBufferCommandIn(String commandIn) { this.bufferCommandIn = commandIn; }
    public void setBufferCommandOut(String commandOut) { this.bufferCommandOut = commandOut; }

    public void pushToBuffer(int value) { buffer.offer(value); }
    public Integer popFromBuffer() { return buffer.poll(); }
    public void clearBuffer() { buffer.clear(); }
    public String getBufferCommandIn() { return bufferCommandIn; }
    public String getBufferCommandOut() { return bufferCommandOut; }

    //判定计数
    public void resetCheckCounts() { this.runCheckCount = 0; this.passedCheckCount = 0; }
    public void incrementRunCheck() { this.runCheckCount++; }
    public void incrementPassedCheck() { this.passedCheckCount++; }
    public boolean isWinConditionMet() { return runCheckCount > 0 && runCheckCount == passedCheckCount; }
    
    //引擎回调
    public void triggerEngineCommand(String statement) { core.triggerEngineCommand(statement); }
    public void setIsPlayerAction(boolean isPlayerAction) { this.isPlayerAction = isPlayerAction; }
    public boolean getandresetIsPlayerAction() { boolean value = this.isPlayerAction; this.isPlayerAction = false; return value; }
}