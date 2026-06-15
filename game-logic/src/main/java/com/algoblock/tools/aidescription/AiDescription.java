package com.algoblock.tools.aidescription;

import com.algoblock.tools.buffer.RowBuffer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 描述文本的慢加载缓存。
 *
 * 数据来源（与代码完全解耦）：
 *   resources/structure/description.json
 *     格式：{ "Queue": "...", "Stack": "...", ... }
 *
 *   resources/structure/{structId 小写}/methodDescription.json
 *     格式：{ "pop": "...", "add": "...", ... }
 *
 * 加载语义：
 *   - 结构级 description.json 全局只读一次（首次访问时加载）
 *   - 方法级 methodDescription.json 按结构 id 粒度缓存：每个结构最多读一次
 *   - 加载失败 / 文件不存在 / 字段缺失 → 静默兜底返回空串，RowBuffer 记一行
 *   - process-wide 单例，跨关卡保留缓存
 *
 * 触发时机：
 *   推荐在关卡装填末尾调 ensureLoaded(structIds) 预热；
 *   ContextBuilder 拼字符串时若结构尚未加载，forXxx 会保险地再调一次 ensureLoaded。
 */
public final class AiDescription {

    private AiDescription() {}

    private static final Gson GSON = new Gson();
    private static final Type STR_MAP_TYPE = new TypeToken<Map<String, String>>(){}.getType();

    /** 结构 id → 结构描述。第一次访问任何 forStructure 时整体加载。 */
    private static Map<String, String> structureDescriptions = null;

    /** 已加载方法描述的结构 id 集合（避免反复尝试已确认不存在的）。 */
    private static final Set<String> loadedMethodStructures = new HashSet<>();

    /** structId → (commandId → description)。按结构粒度填充。 */
    private static final Map<String, Map<String, String>> methodDescriptions = new HashMap<>();

    // ==========================================
    // 对外读取入口
    // ==========================================

    /**
     * 获取结构本身的描述。未加载会先触发加载；最终缺失返回空串。
     */
    public static String forStructure(String structId) {
        ensureStructureMapLoaded();
        if (structureDescriptions == null) return "";
        String desc = structureDescriptions.get(structId);
        return desc == null ? "" : desc;
    }

    /**
     * 获取某结构下某指令的描述。未加载会先触发加载；最终缺失返回空串。
     */
    public static String forMethod(String structId, String commandId) {
        ensureMethodMapLoaded(structId);
        Map<String, String> m = methodDescriptions.get(structId);
        if (m == null) return "";
        String desc = m.get(commandId);
        return desc == null ? "" : desc;
    }

    // ==========================================
    // 预加载入口（关卡装填阶段调用）
    // ==========================================

    /**
     * 显式预加载一组结构 id 的所有描述。
     * 关卡装填末尾调一次即可；后续 forXxx 直接命中缓存。
     */
    public static void ensureLoaded(Iterable<String> structIds) {
        ensureStructureMapLoaded();
        if (structIds == null) return;
        for (String id : structIds) ensureMethodMapLoaded(id);
    }

    // ==========================================
    // 内部加载
    // ==========================================

    private static synchronized void ensureStructureMapLoaded() {
        if (structureDescriptions != null) return;
        Map<String, String> loaded = readJsonMap("structure/description.json");
        structureDescriptions = (loaded == null)
                ? Collections.emptyMap()   // 即使缺失也填空 Map，避免反复尝试
                : loaded;
    }

    private static synchronized void ensureMethodMapLoaded(String structId) {
        if (structId == null || structId.isEmpty()) return;
        if (loadedMethodStructures.contains(structId)) return; // 已尝试加载过（无论成功失败）

        String resource = "structure/" + structId.toLowerCase(Locale.ROOT) + "/methodDescription.json";
        Map<String, String> loaded = readJsonMap(resource);
        methodDescriptions.put(structId, loaded == null ? Collections.emptyMap() : loaded);
        loadedMethodStructures.add(structId);
    }

    /**
     * 从 classpath 读一个 JSON 文件并解析为 Map<String, String>。
     * 文件不存在 → 返回 null；解析失败 → 返回 null 并 RowBuffer 记录。
     */
    private static Map<String, String> readJsonMap(String resource) {
        InputStream is = AiDescription.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) {
            RowBuffer.append("[AiDesc] 文件不存在，跳过: " + resource);
            return null;
        }
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Map<String, String> m = GSON.fromJson(reader, STR_MAP_TYPE);
            if (m == null) {
                RowBuffer.append("[AiDesc] 文件内容为空: " + resource);
                return null;
            }
            RowBuffer.append("[AiDesc] 加载成功: " + resource + "（共 " + m.size() + " 项）");
            return m;
        } catch (Exception e) {
            RowBuffer.append("[AiDesc] 解析失败: " + resource + " | " + e.getMessage());
            return null;
        }
    }
}