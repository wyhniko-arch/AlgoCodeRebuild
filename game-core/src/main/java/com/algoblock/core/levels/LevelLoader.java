package com.algoblock.core.levels;

import com.algoblock.core.levels.Level.InstAllowed;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 从 resources/levels/ 下加载 .json 关卡文件。
 */
public class LevelLoader {

    private static final Gson gson = new Gson();

    /**
     * 加载指定 ID 范围的关卡。
     */
    public List<Level> loadRange(int from, int to) {
        List<Level> levels = new ArrayList<>();
        for (int id = from; id <= to; id++) {
            Level level = load(id);
            if (level != null) {
                levels.add(level);
            }
        }
        return levels;
    }

    /**
     * 加载单个关卡 (如 resources/levels/001.json)。
     */
    public Level load(int id) {
        String path = String.format("/levels/%03d.json", id);
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            Type mapType = new TypeToken<Map<String, Object>>() {
            }.getType();
            Map<String, Object> raw = gson.fromJson(reader, mapType);

            @SuppressWarnings("unchecked")
            List<String> structUsed = (List<String>) raw.getOrDefault("struct_used", List.of());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> instsRaw = (List<Map<String, Object>>) raw.getOrDefault("insts_allowed",
                    List.of());
            List<InstAllowed> instsAllowed = instsRaw.stream().map(m -> {
                String s = (String) m.get("struct");
                String i = (String) m.get("inst_id");
                double max = ((Number) m.get("max_uses")).doubleValue();
                return new InstAllowed(s, i, (int) max);
            }).collect(Collectors.toList());

            @SuppressWarnings("unchecked")
            List<String> initInsts = (List<String>) raw.getOrDefault("init_insts", List.of());

            @SuppressWarnings("unchecked")
            List<String> judgeInsts = (List<String>) raw.getOrDefault("judge_insts", List.of());

            int stepsLimit = ((Number) raw.getOrDefault("steps_limit", 10)).intValue();

            String title = (String) raw.getOrDefault("level_name", "Level " + id);
            String story = (String) raw.getOrDefault("story", "");

            // 推导 input / output 展示文本
            String input = (String) raw.getOrDefault("input_desc",
                    initInsts.isEmpty() ? "?" : String.join("; ", initInsts));
            String output = (String) raw.getOrDefault("output_desc",
                    judgeInsts.isEmpty() ? "?" : String.join("; ", judgeInsts));

            // availableBlocks = max_uses > 0 的指令
            List<String> availableBlocks = instsAllowed.stream()
                    .filter(ia -> ia.maxUses() > 0)
                    .map(ia -> ia.struct() + "_" + ia.instId())
                    .collect(Collectors.toList());

            // forcedBlocks = max_uses == 0 的指令（系统指令，玩家不可用）
            List<String> forcedBlocks = instsAllowed.stream()
                    .filter(ia -> ia.maxUses() == 0)
                    .map(ia -> ia.struct() + "_" + ia.instId())
                    .collect(Collectors.toList());

            // buffer 配置，从关卡 JSON 读取或使用默认值
            @SuppressWarnings("unchecked")
            Map<String, String> buffer = (Map<String, String>) raw.get("buffer");
            String bufferStruct = buffer != null ? buffer.getOrDefault("struct", "") : "";
            String bufferName = buffer != null ? buffer.getOrDefault("name", "") : "";
            String bufferInstIn = buffer != null ? buffer.getOrDefault("inst_in", "") : "";
            String bufferInstOut = buffer != null ? buffer.getOrDefault("inst_out", "") : "";

            return new Level(
                    id, title, story, input, output,
                    availableBlocks, forcedBlocks,
                    structUsed, instsAllowed, initInsts, judgeInsts,
                    stepsLimit,
                    bufferStruct, bufferName, bufferInstIn, bufferInstOut);

        } catch (Exception e) {
            System.err.println("[LevelLoader] 加载关卡 " + id + " 失败: " + e.getMessage());
            return null;
        }
    }
}
