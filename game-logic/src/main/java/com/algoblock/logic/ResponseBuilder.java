package com.algoblock.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
//import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 统一 JSON 响应打包工具。
 *
 * 所有对外响应格式：
 * {
 *   "ok":   true | false,
 *   "type": "ack" | "error" | "debug" | "levelState" | "objects"
 *           | "commandHints" | "levelInfo" | "win" | "fail",
 *   "data": { ... }
 * }
 *
 * interact() 返回的 String[] 始终只含一个元素，即该 JSON 字符串。
 */
public class ResponseBuilder {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    // ==========================================
    // 通用型响应
    // ==========================================

    /** 简单确认，无附加数据 */
    public static String[] ack(String message) {
        JsonObject root = base(true, "ack");
        JsonObject data = new JsonObject();
        data.addProperty("message", message);
        root.add("data", data);
        return wrap(root);
    }

    /** 错误响应 */
    public static String[] error(String message) {
        JsonObject root = base(false, "error");
        JsonObject data = new JsonObject();
        data.addProperty("message", message);
        root.add("data", data);
        return wrap(root);
    }

    // ==========================================
    // 调试（rowbuffer）
    // ==========================================

    /** query:rowbuffer 的响应 */
    public static String[] debug(java.util.List<String> lines) {
        JsonObject root = base(true, "debug");
        JsonObject data = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String line : lines) arr.add(line);
        data.add("lines", arr);
        root.add("data", data);
        return wrap(root);
    }

    // ==========================================
    // 关卡状态（enter 之后：继续 / 失败）
    // ==========================================

    /** 玩家 enter 后仍在继续（既没赢也没超步数） */
    public static String[] levelContinue(int stepsUsed, int stepsLimit) {
        JsonObject root = base(true, "levelState");
        JsonObject data = new JsonObject();
        data.addProperty("status", "continue");
        data.addProperty("stepsUsed", stepsUsed);
        data.addProperty("stepsRemaining", stepsLimit - stepsUsed);
        root.add("data", data);
        return wrap(root);
    }

    /** 过关 */
    public static String[] win() {
        JsonObject root = base(true, "win");
        JsonObject data = new JsonObject();
        data.addProperty("message", "过关！");
        root.add("data", data);
        return wrap(root);
    }

    /** 步数耗尽 */
    public static String[] fail(String reason) {
        JsonObject root = base(false, "fail");
        JsonObject data = new JsonObject();
        data.addProperty("reason", reason);
        root.add("data", data);
        return wrap(root);
    }

    // ==========================================
    // 游戏对象快照（query:objects）
    // ==========================================

    /**
     * 游戏对象快照响应。
     *
     * data.objects 是数组，每个元素：
     * {
     *   "structId": "Stack",
     *   "name":     "B",
     *   "state":    { ... }   // 由 Abstract.Instance.inspectAsJson() 提供的键值对
     * }
     */
    public static String[] objects(java.util.List<JsonObject> objectSnapshots) {
        JsonObject root = base(true, "objects");
        JsonObject data = new JsonObject();
        JsonArray arr = new JsonArray();
        for (JsonObject snapshot : objectSnapshots) arr.add(snapshot);
        data.add("objects", arr);
        root.add("data", data);
        return wrap(root);
    }

    // ==========================================
    // 词法补全（query:nextcommandpart）
    // ==========================================

    /**
     * 词法补全响应。
     *
     * data：
     * {
     *   "buffer":        "当前输入缓冲内容",
     *   "status":        "exact" | "partial" | "dead",
     *   "selectedIndex": 2,
     *   "options":       ["Queue(", "Stack(", ...]
     * }
     */
    public static String[] commandHints(
            String buffer,
            String status,
            int selectedIndex,
            java.util.List<String> options) {
        JsonObject root = base(true, "commandHints");
        JsonObject data = new JsonObject();
        data.addProperty("buffer", buffer);
        data.addProperty("status", status);
        data.addProperty("selectedIndex", selectedIndex);
        JsonArray arr = new JsonArray();
        for (String opt : options) arr.add(opt);
        data.add("options", arr);
        root.add("data", data);
        return wrap(root);
    }

    // ==========================================
    // 关卡元信息（query:levelinfo）
    // ==========================================

    /**
     * 关卡全局信息响应。
     *
     * data：
     * {
     *   "stepsUsed":     2,
     *   "stepsLimit":    6,
     *   "stepsRemaining":4,
     *   "bufferCommandIn":  "Stack(B).push",
     *   "bufferCommandOut": "Stack(B).pop",
     *   "inputDesc":     "Queue(A) = (1,2,3,4)",
     *   "outputDesc":    "Stack(B) top = (4,2,1,3)"
     * }
     */
    public static String[] levelInfo(
            int stepsUsed,
            int stepsLimit,
            String bufferCommandIn,
            String bufferCommandOut,
            String inputDesc,
            String outputDesc) {
        JsonObject root = base(true, "levelInfo");
        JsonObject data = new JsonObject();
        data.addProperty("stepsUsed", stepsUsed);
        data.addProperty("stepsLimit", stepsLimit);
        data.addProperty("stepsRemaining", stepsLimit - stepsUsed);
        data.addProperty("bufferCommandIn", bufferCommandIn != null ? bufferCommandIn : "");
        data.addProperty("bufferCommandOut", bufferCommandOut != null ? bufferCommandOut : "");
        data.addProperty("inputDesc", inputDesc != null ? inputDesc : "");
        data.addProperty("outputDesc", outputDesc != null ? outputDesc : "");
        root.add("data", data);
        return wrap(root);
    }

    // ==========================================
    // 内部工具
    // ==========================================

    private static JsonObject base(boolean ok, String type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("ok", ok);
        obj.addProperty("type", type);
        return obj;
    }

    private static String[] wrap(JsonObject root) {
        return new String[]{GSON.toJson(root)};
    }
}