package com.algoblock.core.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 方块/指令注册表，供 GL 的补全服务和 GamePage 查询可用指令。
 */
public class BlockRegistry {

    private final List<BlockMetaInfo> metas = new ArrayList<>();

    public void register(String name, int arity) {
        metas.add(BlockMetaInfo.of(name, arity));
    }

    public void register(String name, String signature, String description, int arity) {
        metas.add(new BlockMetaInfo(name, signature, description, arity));
    }

    public List<BlockMetaInfo> allMeta() {
        return List.copyOf(metas);
    }
}
