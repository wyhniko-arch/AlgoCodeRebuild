package com.algoblock.tools.jsonloader.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class LevelConfig {

    /** 关卡显示名（仅在关卡内使用，不污染选关界面）。 */
    @SerializedName("level_name")
    public String levelName;

    /** 剧情文本（query:levelinfo 返回，仅关卡内可见）。 */
    public String story;

    /**
     * 关卡作者留给 AI 的私有提示，玩家不可见。
     * ContextBuilder 在装配 query:aiContext 文本时，若非空则会拼入。
     * 用途：当玩家可见的 story 故意写得隐晦时，给 AI 一段直白的引导。
     */
    @SerializedName("ai_hint")
    public String aiHint;

    @SerializedName("input_desc")
    public String inputDesc;

    @SerializedName("output_desc")
    public String outputDesc;

    @SerializedName("struct_used")
    public List<String> structUsed;

    @SerializedName("commands_allowed")
    public List<CommandConfig> commandsAllowed;

    @SerializedName("init_commands")
    public List<String> initCommands;

    @SerializedName("judge_commands")
    public List<String> judgeCommands;

    public BufferConfig buffer;

    @SerializedName("steps_limit")
    public int stepsLimit;

    public static class CommandConfig {
        @SerializedName("struct_id")  public String structId;
        @SerializedName("command_id") public String commandId;
        @SerializedName("max_uses")   public int    maxUses;
    }

    public static class BufferConfig {
        @SerializedName("command_in")  public String commandIn;
        @SerializedName("command_out") public String commandOut;
    }
}