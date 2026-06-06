package com.algoblock.jsonloader.namerule;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class LevelConfig {
    
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
        @SerializedName("struct_id")
        public String structId;
        
        @SerializedName("command_id")
        public String commandId;
        
        @SerializedName("max_uses")
        public int maxUses;
    }

    public static class BufferConfig {
        @SerializedName("command_in")
        public String commandIn;
        
        @SerializedName("command_out")
        public String commandOut;
    }
}