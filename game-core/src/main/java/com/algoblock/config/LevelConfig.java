package com.algoblock.config;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class LevelConfig {
    
    @SerializedName("input_desc")
    public String inputDesc;
    
    @SerializedName("output_desc")
    public String outputDesc;
    
    @SerializedName("struct_used")
    public List<String> structUsed;
    
    @SerializedName("insts_allowed")
    public List<InstConfig> instsAllowed;
    
    @SerializedName("init_insts")
    public List<String> initInsts;
    
    @SerializedName("judge_insts")
    public List<String> judgeInsts;
    
    public BufferConfig buffer;
    
    @SerializedName("steps_limit")
    public int stepsLimit;

    public static class InstConfig {
        public String struct;
        
        @SerializedName("inst_id")
        public String instId;
        
        @SerializedName("max_uses")
        public int maxUses;
    }

    public static class BufferConfig {
        @SerializedName("inst_in")
        public String instIn;
        
        @SerializedName("inst_out")
        public String instOut;
    }
}