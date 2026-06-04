package com.algoblock;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LevelConfigLoader {
    private static final Gson gson = new Gson();

    public static class LevelConfig {
        public List<String> structUsed;
        public Map<String, String> structRegistry;
        public Map<String, Integer> instsAllowed;
        public List<String> initInsts;
        public List<String> judgeInsts;
        public BufferConfig bufferConfig;
        public int stepsLimit;
    }

    public static class BufferConfig {
        public String trigger;
        public String target;
    }

    public static LevelConfig load() {
        try {
            InputStream resourceStream = LevelConfigLoader.class.getClassLoader()
                    .getResourceAsStream("algoblock/levelConfig.json");
            if (resourceStream == null) {
                System.err.println("[ERROR] 关卡配置文件未找到: algoblock/levelConfig.json");
                return null;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8));
            JsonObject root = gson.fromJson(br, JsonObject.class);
            LevelConfig config = gson.fromJson(root.get("level"), LevelConfig.class);
            br.close();

            System.out.println("[INFO] 关卡配置加载成功");
            return config;
        } catch (Exception e) {
            System.err.println("[ERROR] 关卡配置加载失败: " + e.getMessage());
            return null;
        }
    }
}
