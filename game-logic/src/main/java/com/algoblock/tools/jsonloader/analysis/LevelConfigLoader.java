package com.algoblock.tools.jsonloader.analysis;

import com.algoblock.tools.jsonloader.namerule.LevelConfig;
import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class LevelConfigLoader {
    
    // 保持 Gson 实例单例化，避免重复创建带来的开销
    private static final Gson gson = new Gson();

    /**
     * 根据关卡索引加载并解析指定的关卡配置文件
     * * @param levelIndex 关卡索引（如 1, 2）
     * @return 解析完成的 LevelConfig DTO 实例
     * @throws RuntimeException 当文件不存在或解析失败时抛出轻量级运行时异常
     */
    public static LevelConfig getConfig(int levelIndex) {
        // 规范化文件名格式化，例如 1 -> "levels/001.json"
        String fileName = String.format("levels/%03d.json", levelIndex);
        
        // 1. 使用 ClassLoader 从 resources 路径下获取输入流
        InputStream is = LevelConfigLoader.class.getClassLoader().getResourceAsStream(fileName);
        
        if (is == null) {
            // 异常轻量化处理：直接中断并说明原因
            throw new RuntimeException("关卡配置文件未找到: " + fileName);
        }

        // 2. 利用 try-with-resources 规范，确保 Reader 与 InputStream 自动关闭，防止内存泄漏
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            
            // 3. 执行反序列化
            LevelConfig config = gson.fromJson(reader, LevelConfig.class);
            
            if (config == null) {
                throw new RuntimeException("关卡配置文件内容为空: " + fileName);
            }
            
            return config;
            
        } catch (Exception e) {
            // 4. 将受检异常（IOException/JsonSyntaxException）转化为未检查的运行时异常
            // 核心在于包裹原始异常（e），保留堆栈信息，同时降低业务层的异常处理负担
            throw new RuntimeException("解析关卡文件失败: " + fileName, e);
        }
    }
}