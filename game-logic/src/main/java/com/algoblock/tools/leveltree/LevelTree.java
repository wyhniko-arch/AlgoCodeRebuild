package com.algoblock.tools.leveltree;

import com.algoblock.tools.buffer.RowBuffer;
import com.algoblock.tools.jsonloader.loader.NodeRegistryLoader;
import com.algoblock.tools.jsonloader.model.NodeEntry;
import com.algoblock.tools.jsonloader.model.NodeRegistry;
import com.algoblock.tools.savedata.Progress;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 关卡树浏览管理器：当前层级路径、可见节点过滤、通关冒泡。
 *
 * 解锁规则（纯计算，不存于存档）：
 *   节点 N 在父层级 P 中解锁 ⇔ N.requires 中所有 index 都在 progress.cleared[P] 中
 *   空 requires = 初始解锁
 *
 * 可见规则：
 *   未解锁 + hidden=true  → 不可见
 *   其余                  → 可见（VisibleNode.unlocked 字段标识是否可玩）
 *
 * 冒泡规则（applyClear）：
 *   通关一个关卡 K（位于父层级 P）后：
 *     1. progress.cleared[P] += K.index
 *     2. 若 P 的 NodeRegistry 声明了 clearWhen 且 clearWhen ⊆ progress.cleared[P]，
 *        则把 P 视为父层级 P' 下的一个 cleared 节点，递归到 P'。
 *     3. 直到走到根或不再满足条件。
 */
public class LevelTree {

    public enum NodeType { FOLDER, LEVEL, MISSING }

    /** 可见节点视图：包含解锁/通关状态供前端展示。 */
    public static class VisibleNode {
        public final int      index;
        public final String   name;
        public final NodeType type;
        public final boolean  unlocked; // 是否可进入/可玩
        public final boolean  cleared;  // 是否已通关（仅 LEVEL/FOLDER 有意义）

        public VisibleNode(int index, String name, NodeType type, boolean unlocked, boolean cleared) {
            this.index = index; this.name = name; this.type = type;
            this.unlocked = unlocked; this.cleared = cleared;
        }
    }

    // ---- 浏览状态 ----
    private String currentPath = "";
    private NodeRegistry currentRegistry;
    private List<VisibleNode> visibleCache;

    public LevelTree(Progress progress) {
        enter("", progress);
    }

    public String getCurrentPath() { return currentPath; }

    public List<VisibleNode> getVisibleNodes() {
        return (visibleCache == null) ? new ArrayList<>() : visibleCache;
    }

    public void reset(Progress progress) { enter("", progress); }

    /** 返回上一层。已在根则不动。 */
    public boolean back(Progress progress) {
        if (currentPath.isEmpty()) return false;
        int slash = currentPath.lastIndexOf('/');
        String parent = (slash < 0) ? "" : currentPath.substring(0, slash);
        enter(parent, progress);
        return true;
    }

    /**
     * 按可见列表 1-based 序号取模选择节点。
     * 文件夹 → 进入并返回 null；关卡 → 返回该关卡完整路径。
     * 锁住的节点不会被选中（取模也只在可玩节点中？不，取模在可见列表中——
     * 前端可以拿到 unlocked 字段，但 start 时如果选到锁住的节点直接拒绝）。
     */
    public StartResult start(int oneBasedIndex, Progress progress) {
        List<VisibleNode> vis = getVisibleNodes();
        if (vis.isEmpty()) return StartResult.empty();
        int n = vis.size();
        int idx = ((oneBasedIndex - 1) % n + n) % n;
        VisibleNode picked = vis.get(idx);

        if (!picked.unlocked) return StartResult.locked(picked.name);

        if (picked.type == NodeType.FOLDER) {
            String next = currentPath.isEmpty() ? picked.name : currentPath + "/" + picked.name;
            enter(next, progress);
            return StartResult.enteredFolder();
        }
        String levelPath = currentPath.isEmpty() ? picked.name : currentPath + "/" + picked.name;
        return StartResult.startedLevel(levelPath);
    }

    /** start() 的返回值：区分"进了文件夹"、"开始了关卡"、"选了锁住的"、"列表为空"。 */
    public static class StartResult {
        public enum Kind { EMPTY, LOCKED, ENTERED_FOLDER, STARTED_LEVEL }
        public final Kind   kind;
        public final String levelPath;  // 仅 STARTED_LEVEL 有
        public final String lockedName; // 仅 LOCKED 有
        private StartResult(Kind k, String lp, String ln) {
            this.kind = k; this.levelPath = lp; this.lockedName = ln;
        }
        public static StartResult empty()                       { return new StartResult(Kind.EMPTY, null, null); }
        public static StartResult locked(String name)           { return new StartResult(Kind.LOCKED, null, name); }
        public static StartResult enteredFolder()               { return new StartResult(Kind.ENTERED_FOLDER, null, null); }
        public static StartResult startedLevel(String fullPath) { return new StartResult(Kind.STARTED_LEVEL, fullPath, null); }
    }

    // ==========================================
    // 加载并过滤当前层级
    // ==========================================

    public void enter(String path, Progress progress) {
        currentPath     = path == null ? "" : path;
        currentRegistry = NodeRegistryLoader.load(currentPath);
        visibleCache    = buildVisible(progress);
    }

    /**
     * 根据 NodeRegistry 和 Progress 算出可见节点列表。
     * 探测每个 name 在 classpath 下的实际类型；MISSING 静默丢弃。
     */
    private List<VisibleNode> buildVisible(Progress progress) {
        List<VisibleNode> out = new ArrayList<>();
        if (currentRegistry == null || currentRegistry.nodes == null) return out;

        Set<Integer> doneSet = (progress == null) ? new HashSet<>()
                : new HashSet<>(progress.clearedAt(currentPath));

        for (NodeEntry e : currentRegistry.nodes) {
            NodeType type = probeType(currentPath, e.name);
            if (type == NodeType.MISSING) {
                RowBuffer.append("[Tree] 注册条目实际不存在，忽略: "
                        + (currentPath.isEmpty() ? e.name : currentPath + "/" + e.name));
                continue;
            }
            boolean unlocked = computeUnlocked(e, doneSet);
            if (!unlocked && e.hidden) continue; // 隐藏关：未解锁时不出现

            boolean cleared = doneSet.contains(e.index);
            out.add(new VisibleNode(e.index, e.name, type, unlocked, cleared));
        }
        return out;
    }

    /**
     * 解锁判定：requires 中已 cleared 的数量 ≥ requires.size() - skippable。
     *
     * 例：
     *   requires=[1,2,3], skippable=0 → 1、2、3 全 cleared 才解锁
     *   requires=[1,2,3], skippable=1 → 任意 2 个 cleared 即解锁
     *   requires=[1,2,3], skippable=3 → 等同于无前置
     *   requires 为空                 → 初始解锁
     */
    private static boolean computeUnlocked(NodeEntry e, Set<Integer> doneSet) {
        if (e.requires == null || e.requires.isEmpty()) return true;
        int need = e.requires.size() - Math.max(0, e.skippable);
        if (need <= 0) return true; // skippable 足以跳过全部
        int done = 0;
        for (Integer req : e.requires) {
            if (doneSet.contains(req)) {
                done++;
                if (done >= need) return true;
            }
        }
        return false;
    }

    /**
     * 探测 levels/{path}/{name} 是文件夹还是关卡。
     * 文件夹判定：内部存在 nodeRegistry.json。
     */
    private static NodeType probeType(String path, String name) {
        String base = path.isEmpty() ? "levels/" : "levels/" + path + "/";
        ClassLoader cl = LevelTree.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(base + name + "/nodeRegistry.json")) {
            if (is != null) return NodeType.FOLDER;
        } catch (Exception ignored) {}
        try (InputStream is = cl.getResourceAsStream(base + name + ".json")) {
            if (is != null) return NodeType.LEVEL;
        } catch (Exception ignored) {}
        return NodeType.MISSING;
    }

    // ==========================================
    // 通关冒泡（applyClear）
    // ==========================================

    /**
     * 关卡通关时调用：
     *   1. 把本关 index 标记到父层级 cleared
     *   2. 检查父层级的 clear_when 是否被满足；若是，把父层级作为祖父层级下的节点 cleared
     *   3. 递归向上直至不满足或到根
     *
     * 整个过程只读 NodeRegistry，写 progress.cleared，无任何额外存档冗余。
     */
    public static void applyClear(String fullPath, Progress progress) {
        if (progress == null || fullPath == null || fullPath.isEmpty()) return;

        String currentLevelPath = fullPath;
        while (true) {
            int slash = currentLevelPath.lastIndexOf('/');
            String parent = (slash < 0) ? "" : currentLevelPath.substring(0, slash);
            String leaf   = (slash < 0) ? currentLevelPath : currentLevelPath.substring(slash + 1);

            NodeRegistry parentReg = NodeRegistryLoader.load(parent);
            if (parentReg == null || parentReg.nodes == null) return;

            // 找到 leaf 对应的 index
            Integer leafIndex = null;
            for (NodeEntry e : parentReg.nodes) {
                if (leaf.equals(e.name)) { leafIndex = e.index; break; }
            }
            if (leafIndex == null) return;

            boolean changed = progress.markCleared(parent, leafIndex);
            if (!changed) return; // 已经标记过，停止冒泡防止循环

            // 检查 parent 自身能否被视为 cleared 并继续向上
            if (parent.isEmpty()) return; // 已到根
            if (parentReg.clearWhen == null || parentReg.clearWhen.isEmpty()) return;
            Set<Integer> parentDone = progress.clearedAt(parent);
            int need = parentReg.clearWhen.size() - Math.max(0, parentReg.clearWhenSkippable);
            if (need > 0) {
                int done = 0;
                for (Integer req : parentReg.clearWhen) {
                    if (parentDone.contains(req)) done++;
                }
                if (done < need) return; // 条件未满足，停止冒泡
            }
            // need <= 0 视为永远满足；否则 done >= need 才到此
            currentLevelPath = parent;
        }
    }

    /**
     * 计算给定关卡路径的"父路径"以便回到父层级。
     * @return parentPath；根关卡返回 ""
     */
    public static String parentOf(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) return "";
        int slash = fullPath.lastIndexOf('/');
        return (slash < 0) ? "" : fullPath.substring(0, slash);
    }
}