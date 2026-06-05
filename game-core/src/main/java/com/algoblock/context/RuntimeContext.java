package com.algoblock.context;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.algoblock.core.Core;
import com.algoblock.structure.Abstract;

public class RuntimeContext {
    private final Core core;
    public RuntimeContext(Core core) { this.core = core; }

    private final Map<String, Abstract> objects = new HashMap<>();
    private final Queue<Integer> buffer = new LinkedList<>();
    
    private boolean isPlayerAction;
    private String bufferInstIn;
    private String bufferInstOut;

    private int runCheckCount = 0;
    private int passedCheckCount = 0;

    private String generateKey(String struct, String name) { return struct + "_" + name; }

    public void putObject(String struct, String name, Abstract obj) {
        System.out.println("[Debug] [Context] 注册游戏对象: " + struct + " -> " + name);
        objects.put(generateKey(struct, name), obj);
    }

    public Abstract getObject(String struct, String name) { return objects.get(generateKey(struct, name)); }

    public void removeObject(String struct, String name) { 
        System.out.println("[Debug] [Context] 抹除游戏对象: " + struct + " -> " + name);
        objects.remove(generateKey(struct, name)); 
    }

    /**
     * [重构点]: 基于结构ID严格过滤命名空间。Queue 的指令只能联想出 Queue 的对象。
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

    public void setBufferConfig(String instIn, String instOut) {
        this.bufferInstIn = instIn;
        this.bufferInstOut = instOut;
    }

    public void pushToBuffer(int value) { buffer.offer(value); }
    public Integer popFromBuffer() { return buffer.poll(); }
    public void clearBuffer() { buffer.clear(); }
    public String getBufferInstIn() { return bufferInstIn; }
    public String getBufferInstOut() { return bufferInstOut; }
    public void resetCheckCounts() { this.runCheckCount = 0; this.passedCheckCount = 0; }
    public void incrementRunCheck() { this.runCheckCount++; }
    public void incrementPassedCheck() { this.passedCheckCount++; }
    public boolean isWinConditionMet() { return runCheckCount > 0 && runCheckCount == passedCheckCount; }
    public void triggerEngineCommand(String statement) { core.triggerEngineCommand(statement); }
    public void setIsPlayerAction( boolean isPlayerAction) { this.isPlayerAction = isPlayerAction;}
    public boolean getandresetIsPlayerAction() { boolean value = this.isPlayerAction; this.isPlayerAction = false; return value; }
}