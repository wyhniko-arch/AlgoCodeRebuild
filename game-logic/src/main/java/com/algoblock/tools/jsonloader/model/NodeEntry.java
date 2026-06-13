package com.algoblock.tools.jsonloader.model;

import java.util.List;

/**
 * nodeRegistry.json 的一条节点记录。
 *
 * 例：
 *   { "index": 3, "name": "step03", "requires": [1, 2] }
 *   { "index": 7, "name": "final",  "requires": [4, 5, 6], "skippable": 1 }
 *   { "index": 9, "name": "secret", "requires": [8], "hidden": true }
 *
 * 字段（除 index、name 外都可省略，省略时取默认值）：
 *   index      — 同层级内唯一，用于 requires / clear_when 引用以及存档 cleared 标识
 *   name       — 同层级下文件夹（无后缀）或 JSON 关卡（去掉 .json）的名称
 *   requires   — 前置 index 列表；省略/空 = 初始解锁
 *   skippable  — 可不完成的前置关卡数量。默认 0（即 requires 必须全部 cleared）。
 *                例：requires=[4,5,6], skippable=1 → 通过 4/5/6 中任意 2 个即可解锁。
 *                若 skippable >= requires.size() 则等同于无前置，初始解锁。
 *   hidden     — 未解锁时是否完全隐藏。默认 false。
 */
public class NodeEntry {
    public int    index;
    public String name;

    /** 前置依赖列表。空/null = 无前置 = 初始解锁。 */
    public List<Integer> requires;

    /**
     * 可跳过的前置数量。
     * 实际需要通关的前置数 = max(0, requires.size() - skippable)。
     * JSON 中省略此字段默认为 0。
     */
    public int skippable;

    /** 隐藏关：未解锁时是否不出现在 browse。JSON 中省略默认为 false。 */
    public boolean hidden;
}