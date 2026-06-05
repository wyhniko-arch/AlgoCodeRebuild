package com.algoblock.config;

import java.util.HashMap;
import java.util.Map;

public class StructureRegistry {
    // 内部持有一个扁平化的映射表，对应 JSON 中的 "Queue": "queue/FakeQueue.java"
    private Map<String, String> mappings = new HashMap<>();

    public StructureRegistry(Map<String, String> mappings) {
        if (mappings != null) {
            this.mappings = mappings;
        }
    }

    /**
     * 根据结构体名称获取其相对路径
     * @param structName 结构体 ID (如 "Queue")
     * @return 相对路径 (如 "queue/FakeQueue.java")
     */
    public String getPath(String structName) {
        return mappings.get(structName);
    }
}