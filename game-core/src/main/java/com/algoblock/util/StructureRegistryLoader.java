package com.algoblock.util;

import com.algoblock.config.StructureRegistry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class StructureRegistryLoader {
    private static final Gson gson = new Gson();
    private static final String REGISTRY_PATH = "structure/registry.json";

    public static StructureRegistry getRegistry() {
        InputStream is = StructureRegistryLoader.class.getClassLoader().getResourceAsStream(REGISTRY_PATH);
        if (is == null) {
            throw new RuntimeException("全局结构体注册表文件未找到: " + REGISTRY_PATH);
        }

        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> rawMap = gson.fromJson(reader, type);
            // 实例化时自动触发构造函数中的路径逻辑解析
            return new StructureRegistry(rawMap);
        } catch (Exception e) {
            throw new RuntimeException("解析全局结构体注册表失败: " + REGISTRY_PATH, e);
        }
    }
}