package com.algoblock.structure;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MethodRegistryLoader {
    private static final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    public static Map<String, String> load(String structureId) {
        Map<String, String> registry = new HashMap<>();
        if (structureId == null || structureId.isEmpty()) {
            System.err.println("[ERROR] structureId 不能为空");
            return registry;
        }

        String normalizedId = structureId.toLowerCase(Locale.ROOT);
        String resourcePath = "structure/" + normalizedId + "/methodRegistry.json";

        try (InputStream resourceStream = MethodRegistryLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (resourceStream == null) {
                System.err.println("[ERROR] 方法注册表文件未找到: " + resourcePath);
                return registry;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8))) {
                Map<String, String> loadedRegistry = gson.fromJson(br, MAP_TYPE);
                if (loadedRegistry != null) {
                    registry.putAll(loadedRegistry);
                    System.out.println("[INFO] 方法注册表加载成功: " + resourcePath + "，共 " + registry.size() + " 项");
                } else {
                    System.err.println("[ERROR] 方法注册表解析结果为空: " + resourcePath);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] 方法注册表加载失败: " + e.getMessage());
        }
        return registry;
    }
}
