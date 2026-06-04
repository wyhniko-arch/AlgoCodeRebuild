package com.algoblock.core.engine;

/**
 * 方块/指令元信息，供 GL 补全服务使用。
 */
public record BlockMetaInfo(String name, String signature, String description, int arity) {

    public static BlockMetaInfo of(String name, int arity) {
        return new BlockMetaInfo(name, "?", "", arity);
    }
}
