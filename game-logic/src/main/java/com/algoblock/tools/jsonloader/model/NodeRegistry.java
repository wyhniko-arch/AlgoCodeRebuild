package com.algoblock.tools.jsonloader.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * {层级目录}/nodeRegistry.json 对应的 POJO。
 *
 * 字段：
 *   nodes              — 节点条目，按声明顺序决定选关界面展示顺序
 *   clearWhen          — 可选。本层级被视为 cleared 的前置 index 列表。
 *   clearWhenSkippable — 可选。clearWhen 中可不完成的数量，默认 0。
 *                        例：clearWhen=[1,2,3], clearWhenSkippable=1
 *                        → 这 3 个里任意 2 个 cleared 即视为本文件夹通关。
 *                        若 clearWhen 为空，本层级永远不会自动 cleared。
 */
public class NodeRegistry {

    public List<NodeEntry> nodes;

    @SerializedName("clear_when")
    public List<Integer> clearWhen;

    /** clear_when 中可不完成的数量，省略默认 0。 */
    @SerializedName("clear_when_skippable")
    public int clearWhenSkippable;
}