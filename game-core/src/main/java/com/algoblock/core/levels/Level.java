package com.algoblock.core.levels;

import java.util.List;

/**
 * 关卡配置数据。由 LevelLoader 从 JSON 反序列化填充。
 */
public record Level(
        int id,
        String title,
        String story,
        String input,
        String output,
        List<String> availableBlocks,
        List<String> forcedBlocks,
        // 以下为引擎执行所需字段
        List<String> structUsed,
        List<InstAllowed> instsAllowed,
        List<String> initInsts,
        List<String> judgeInsts,
        int stepsLimit,
        String bufferStruct,
        String bufferName,
        String bufferInstIn,
        String bufferInstOut) {

    public record InstAllowed(String struct, String instId, int maxUses) {
    }
}
