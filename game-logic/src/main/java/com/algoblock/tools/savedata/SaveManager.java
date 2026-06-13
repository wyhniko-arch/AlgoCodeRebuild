package com.algoblock.tools.savedata;

import com.algoblock.tools.buffer.RowBuffer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 存档管理器：单存档，落盘到工作目录侧的 saves/save.json。
 *
 * 安全约束：所有写入路径都强制在 saves/ 目录下，避免越权写入其他位置。
 * 文件不存在时自动创建空存档。
 *
 * "绿色"含义：路径相对当前工作目录，便于跟随安装包整体迁移。
 */
public class SaveManager {

    private static final Path SAVE_DIR  = Paths.get("saves").toAbsolutePath().normalize();
    private static final Path SAVE_FILE = SAVE_DIR.resolve("save.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private SaveManager() {}

    /** 加载存档；不存在或损坏则返回全新空 Progress。 */
    public static Progress load() {
        ensureSaveDir();
        if (!Files.exists(SAVE_FILE)) {
            RowBuffer.append("[Save] 存档不存在，使用空存档: " + SAVE_FILE);
            Progress p = new Progress();
            p.normalize();
            return p;
        }
        try {
            String json = new String(Files.readAllBytes(SAVE_FILE), StandardCharsets.UTF_8);
            Progress p = GSON.fromJson(json, Progress.class);
            if (p == null) p = new Progress();
            p.normalize();
            RowBuffer.append("[Save] 加载存档成功: " + SAVE_FILE);
            return p;
        } catch (Exception e) {
            RowBuffer.append("[Save] 加载存档失败，回退到空存档: " + e.getMessage());
            Progress p = new Progress();
            p.normalize();
            return p;
        }
    }

    /** 把 Progress 落盘。文件只会写到 SAVE_FILE，不接受其他路径。 */
    public static boolean save(Progress progress) {
        ensureSaveDir();
        try {
            // 二次安全校验：实际写入路径必须落在 SAVE_DIR 内
            Path target = SAVE_FILE.normalize();
            if (!target.startsWith(SAVE_DIR)) {
                RowBuffer.append("[Save] 拒绝越权写入: " + target);
                return false;
            }
            String json = GSON.toJson(progress == null ? new Progress() : progress);
            Files.write(target, json.getBytes(StandardCharsets.UTF_8));
            RowBuffer.append("[Save] 存档已写入: " + target);
            return true;
        } catch (IOException e) {
            RowBuffer.append("[Save] 写入失败: " + e.getMessage());
            return false;
        }
    }

    /** 重置存档：删除磁盘文件并返回新空 Progress。 */
    public static Progress reset() {
        try {
            if (Files.exists(SAVE_FILE)) {
                Files.delete(SAVE_FILE);
                RowBuffer.append("[Save] 存档文件已删除");
            }
        } catch (IOException e) {
            RowBuffer.append("[Save] 删除存档失败: " + e.getMessage());
        }
        Progress p = new Progress();
        p.normalize();
        // 立刻把空存档落盘，保证下次启动也是干净状态
        save(p);
        return p;
    }

    /** 确保 saves/ 目录存在；只会在工作目录创建该目录，不会触及其他位置。 */
    private static void ensureSaveDir() {
        try {
            if (!Files.exists(SAVE_DIR)) {
                Files.createDirectories(SAVE_DIR);
                RowBuffer.append("[Save] 创建存档目录: " + SAVE_DIR);
            }
        } catch (IOException e) {
            RowBuffer.append("[Save] 创建存档目录失败: " + e.getMessage());
        }
    }
}