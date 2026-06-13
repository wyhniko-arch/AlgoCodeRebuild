package com.algoblock.tools.savedata;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 存档数据模型：仅记录通关状态。
 *
 * 解锁状态完全由 cleared + 各节点的 requires 计算得出，
 * 不在存档中冗余 unlocked 字段，避免来源-目标对的膨胀和不一致。
 *
 * 结构：
 *   cleared: parentPath -> { index, ... }
 * parentPath 用 '/' 分隔，根目录为空字符串 ""。
 *
 * 由 SaveManager 序列化为 saves/save.json。
 */
public class Progress {

    public Map<String, Set<Integer>> cleared = new HashMap<>();

    public boolean isCleared(String parentPath, int index) {
        Set<Integer> s = cleared.get(parentPath == null ? "" : parentPath);
        return s != null && s.contains(index);
    }

    /** 返回某层级下所有已通关的 index 集合（只读，不会为 null）。 */
    public Set<Integer> clearedAt(String parentPath) {
        Set<Integer> s = cleared.get(parentPath == null ? "" : parentPath);
        return s == null ? java.util.Collections.emptySet() : s;
    }

    /**
     * 标记 cleared。返回是否产生了实际变化（true=新加入，false=本来就在）。
     * 用返回值判断是否需要继续向上冒泡。
     */
    public boolean markCleared(String parentPath, int index) {
        return cleared.computeIfAbsent(parentPath == null ? "" : parentPath,
                k -> new HashSet<>()).add(index);
    }

    /** Gson 反序列化后 Map 可能为 null，统一兜底。 */
    public void normalize() {
        if (cleared == null) cleared = new HashMap<>();
    }
}