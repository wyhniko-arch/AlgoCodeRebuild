package com.algoblock.tools.jsonloader.loader;
 
import com.algoblock.tools.jsonloader.model.LevelConfig;
import com.google.gson.Gson;
 
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
 
/**
 * 关卡 JSON 加载器。
 * 新版按层级路径加载，例如 path="tutorial/basics/step01" 加载
 * resources/levels/tutorial/basics/step01.json。
 */
public class LevelConfigLoader {
    
    // 保持 Gson 实例单例化，避免重复创建带来的开销
    private static final Gson gson = new Gson();

    /**
     * @param path 关卡相对路径（不含 .json 后缀），'/' 分隔
     * @return 解析完成的 LevelConfig
     * @throws RuntimeException 文件不存在或解析失败
     */
    public static LevelConfig getConfig(String path) {
        if (path == null || path.isEmpty()) {
            throw new RuntimeException("关卡路径为空");
        }
        String resource = "levels/" + path + ".json";
        InputStream is = LevelConfigLoader.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) throw new RuntimeException("关卡配置文件未找到: " + resource);
 
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            LevelConfig config = gson.fromJson(reader, LevelConfig.class);
            if (config == null) throw new RuntimeException("关卡配置文件内容为空: " + resource);
            return config;
        } catch (Exception e) {
            throw new RuntimeException("解析关卡文件失败: " + resource, e);
        }
    }
}