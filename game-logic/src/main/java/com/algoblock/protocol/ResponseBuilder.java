package com.algoblock.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 统一 JSON 响应打包工具。
 *
 * 所有对外响应格式：
 * { "ok": true|false, "type": "...", "data": { ... } }
 * interact() 返回的 String[] 始终只含一个元素，即该 JSON 字符串。
 */

/* ok 字段的作用
    每个响应顶层都有 "ok": true|false。它的作用：
    让前端 / 调用方在不解析 type 的情况下，仅凭一个布尔字段就能判断"这次调用成功还是失败"。这是 HTTP 状态码模式的迷你版。

        说明：每条响应的 ok 都和 type 一一对应（type 是 error/fail 就 ok=false，否则 ok=true）。
        如果站在这个角度看，ok 是冗余的——前端完全可以靠 type==="error"||type==="fail" 判断。
    
    具体使用场景：
    前端代码可以统一写 if (resp.ok) { ... 正常处理 ... } else { 弹错误 toast }，不必为每种 type 都写一个分支
    失败的 type（error、fail）ok=false，其余 ok=true

    当前两个 ok=false 的位置：

    error() — 非法指令、参数错误、未解锁节点等
    fail() — 步数耗尽

    其它都是 ok=true，即使是 win、debug 这种"业务结果"或"调试输出"。
    它的价值 = 把"出错了"和"业务发生了什么"两个维度分开。ok 答出错与否，type 答业务语义。
*/
public class ResponseBuilder {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static String[] ack(String message) {
        JsonObject root = base(true, "ack");
        JsonObject data = new JsonObject();
        data.addProperty("message", message);
        root.add("data", data);
        return wrap(root);
    }

    public static String[] error(String message) {
        JsonObject root = base(false, "error");
        JsonObject data = new JsonObject();
        data.addProperty("message", message);
        root.add("data", data);
        return wrap(root);
    }

    public static String[] debug(java.util.List<String> lines) {
        JsonObject root = base(true, "debug");
        JsonObject data = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String l : lines) arr.add(l);
        data.add("lines", arr);
        root.add("data", data);
        return wrap(root);
    }

    public static String[] levelContinue(int stepsUsed, int stepsLimit) {
        JsonObject root = base(true, "levelState");
        JsonObject data = new JsonObject();
        data.addProperty("status", "continue");
        data.addProperty("stepsUsed", stepsUsed);
        data.addProperty("stepsRemaining", stepsLimit - stepsUsed);
        root.add("data", data);
        return wrap(root);
    }

    public static String[] win() {
        JsonObject root = base(true, "win");
        JsonObject data = new JsonObject();
        data.addProperty("message", "过关！");
        root.add("data", data);
        return wrap(root);
    }

    public static String[] fail(String reason) {
        JsonObject root = base(false, "fail");
        JsonObject data = new JsonObject();
        data.addProperty("reason", reason);
        root.add("data", data);
        return wrap(root);
    }

    public static String[] objects(java.util.List<JsonObject> objectSnapshots) {
        JsonObject root = base(true, "objects");
        JsonObject data = new JsonObject();
        JsonArray arr = new JsonArray();
        for (JsonObject s : objectSnapshots) arr.add(s);
        data.add("objects", arr);
        root.add("data", data);
        return wrap(root);
    }

    public static String[] commandHints(String buffer, String status,
                                        int selectedIndex, java.util.List<String> options) {
        JsonObject root = base(true, "commandHints");
        JsonObject data = new JsonObject();
        data.addProperty("buffer", buffer);
        data.addProperty("status", status);
        data.addProperty("selectedIndex", selectedIndex);
        JsonArray arr = new JsonArray();
        for (String o : options) arr.add(o);
        data.add("options", arr);
        root.add("data", data);
        return wrap(root);
    }

    /** 关卡内信息：含 levelName / story / path 等关卡级字段。 */
    public static String[] levelInfo(String levelName, String story, String path,
                                     int stepsUsed, int stepsLimit,
                                     String bufferCommandIn, String bufferCommandOut,
                                     String inputDesc, String outputDesc) {
        JsonObject root = base(true, "levelInfo");
        JsonObject data = new JsonObject();
        data.addProperty("levelName",        safe(levelName));
        data.addProperty("story",            safe(story));
        data.addProperty("path",             safe(path));
        data.addProperty("stepsUsed",        stepsUsed);
        data.addProperty("stepsLimit",       stepsLimit);
        data.addProperty("stepsRemaining",   stepsLimit - stepsUsed);
        data.addProperty("bufferCommandIn",  safe(bufferCommandIn));
        data.addProperty("bufferCommandOut", safe(bufferCommandOut));
        data.addProperty("inputDesc",        safe(inputDesc));
        data.addProperty("outputDesc",       safe(outputDesc));
        root.add("data", data);
        return wrap(root);
    }

    /**
     * 选关界面信息。
     *
     * data：
     * {
     *   "path": "tutorial/basics",
     *   "nodes": [
     *     { "ordinal": 1, "name": "step01", "type": "level",  "unlocked": true,  "cleared": true  },
     *     { "ordinal": 2, "name": "step02", "type": "level",  "unlocked": true,  "cleared": false },
     *     { "ordinal": 3, "name": "step03", "type": "level",  "unlocked": false, "cleared": false }
     *   ]
     * }
     *
     * 锁住的节点（unlocked=false）也会出现在列表里，前端按需展示为灰态；
     * 隐藏关（hidden=true 且未解锁）则根本不会出现在 nodes 中。
     */
    public static String[] browse(String currentPath, java.util.List<NodeView> nodes) {
        JsonObject root = base(true, "browse");
        JsonObject data = new JsonObject();
        data.addProperty("path", safe(currentPath));
        JsonArray arr = new JsonArray();
        int ord = 1;
        for (NodeView n : nodes) {
            JsonObject o = new JsonObject();
            o.addProperty("ordinal",  ord++);
            o.addProperty("name",     n.name);
            o.addProperty("type",     n.type);
            o.addProperty("unlocked", n.unlocked);
            o.addProperty("cleared",  n.cleared);
            arr.add(o);
        }
        data.add("nodes", arr);
        root.add("data", data);
        return wrap(root);
    }

    /** browse 用的轻量结构体，避免 ResponseBuilder 反向依赖 LevelTree。 */
    public static class NodeView {
        public final String  name;
        public final String  type;     // "level" / "folder"
        public final boolean unlocked;
        public final boolean cleared;
        public NodeView(String name, String type, boolean unlocked, boolean cleared) {
            this.name = name; this.type = type; this.unlocked = unlocked; this.cleared = cleared;
        }
    }

    /**
     * 玩家配额状态。
     *
     * data：
     * {
     *   "items": [
     *     { "structId":"Queue", "commandId":"pop",  "pattern":"Queue[@].pop",  "used":3, "max":6, "remaining":3, "exhausted":false },
     *     { "structId":"Queue", "commandId":"add",  "pattern":"Queue[@].add",  "used":1, "max":1, "remaining":0, "exhausted":true  },
     *     ...
     *   ]
     * }
     *
     * 只包含"玩家初始有配额（max>0）"的指令：
     *   - max>0 且 remaining>0  → 仍可用
     *   - max>0 且 remaining==0 → 已用完（前端渲染为 disabled）
     * max==0 的指令（系统专用、玩家不可见）不出现在此列表。
     */
    public static String[] quota(java.util.List<QuotaItem> items) {
        JsonObject root = base(true, "quota");
        JsonObject data = new JsonObject();
        JsonArray arr = new JsonArray();
        for (QuotaItem q : items) {
            JsonObject o = new JsonObject();
            o.addProperty("structId",  q.structId);
            o.addProperty("commandId", q.commandId);
            o.addProperty("pattern",   q.pattern);
            o.addProperty("used",      q.used);
            o.addProperty("max",       q.max);
            o.addProperty("remaining", q.max - q.used);
            o.addProperty("exhausted", q.used >= q.max);
            arr.add(o);
        }
        data.add("items", arr);
        root.add("data", data);
        return wrap(root);
    }

    /** quota 用的轻量结构体，避免 ResponseBuilder 反向依赖 CommandDefinition。 */
    public static class QuotaItem {
        public final String structId;
        public final String commandId;
        public final String pattern;
        public final int    used;
        public final int    max;
        public QuotaItem(String structId, String commandId, String pattern, int used, int max) {
            this.structId  = structId;
            this.commandId = commandId;
            this.pattern   = pattern;
            this.used      = used;
            this.max       = max;
        }
    }

    // ==========================================
    // 内部工具
    // ==========================================

    private static String safe(String s) { return s == null ? "" : s; }

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