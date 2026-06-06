package com.algoblock.jsonloader.namerule;

import java.util.HashMap;
import java.util.Map;

public class StructureRegistry {
    private final Map<String, String> fqcnMap = new HashMap<>();
    private static final String BASE_PACKAGE = "com.algoblock.structure.";

    public StructureRegistry(Map<String, String> rawMap) {
        // 在构造阶段完成逻辑规范化
        for (Map.Entry<String, String> entry : rawMap.entrySet()) {
            String relativePath = entry.getValue();
            
            String dotNotation = relativePath.replace('/', '.');
            if (dotNotation.endsWith(".java")) {
                dotNotation = dotNotation.substring(0, dotNotation.length() - 5);
            }
            
            fqcnMap.put(entry.getKey(), BASE_PACKAGE + dotNotation);
        }
    }

    public String getFQCN(String structKey) {
        return fqcnMap.get(structKey);
    }
}