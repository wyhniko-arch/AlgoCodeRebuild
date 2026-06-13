package com.algoblock.tools.jsonloader.loader;

import com.algoblock.tools.buffer.RowBuffer;
import com.algoblock.tools.jsonloader.model.NodeRegistry;
import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * 从 resources/levels/{path}/nodeRegistry.json 加载层级注册表。
 * path 用 '/' 分隔；空字符串表示 levels 根目录。
 */
public class NodeRegistryLoader {

    private static final Gson gson = new Gson();

    /**
     * @param path 层级目录在 levels 下的相对路径，'/' 分隔，空字符串=根
     * @return NodeRegistry；文件不存在或解析失败返回 null（由调用方处理）
     */
    public static NodeRegistry load(String path) {
        String resource = (path == null || path.isEmpty())
                ? "levels/nodeRegistry.json"
                : "levels/" + path + "/nodeRegistry.json";

        InputStream is = NodeRegistryLoader.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) {
            RowBuffer.append("[Tree] 层级注册表不存在: " + resource);
            return null;
        }
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            NodeRegistry reg = gson.fromJson(reader, NodeRegistry.class);
            if (reg == null || reg.nodes == null) {
                RowBuffer.append("[Tree] 注册表为空: " + resource);
                return null;
            }
            RowBuffer.append("[Tree] 加载注册表成功: " + resource + " (节点数=" + reg.nodes.size() + ")");
            return reg;
        } catch (Exception e) {
            RowBuffer.append("[Tree] 注册表解析失败: " + resource + " | " + e.getMessage());
            return null;
        }
    }
}