package com.algoblock.logic;

import com.algoblock.api.ResponseBuilder;
import com.algoblock.tools.leveltree.LevelTree;
import com.algoblock.tools.savedata.Progress;

import java.util.ArrayList;
import java.util.List;

/**
 * 选关浏览适配层：把 LevelTree 的内部数据结构转换为对外的 browse 响应，
 * 并处理 action:start:<n> 的四种 StartResult 分支。
 *
 * 设计为无状态：所有数据通过参数传入。
 */
public final class Browser {

    private Browser() {}

    /** 把 LevelTree 当前可见节点映射为 ResponseBuilder.NodeView 并打包 browse 响应。 */
    public static String[] buildBrowseResponse(LevelTree levelTree) {
        List<LevelTree.VisibleNode> vis = levelTree.getVisibleNodes();
        List<ResponseBuilder.NodeView> views = new ArrayList<>();
        for (LevelTree.VisibleNode v : vis) {
            String typeStr = (v.type == LevelTree.NodeType.FOLDER) ? "folder" : "level";
            views.add(new ResponseBuilder.NodeView(v.name, typeStr, v.unlocked, v.cleared));
        }
        return ResponseBuilder.browse(levelTree.getCurrentPath(), views);
    }

    /**
     * action:start:<n> 实现。
     * 1-based 取模选可见节点。文件夹则进入返回新 browse；
     * 关卡则真正开始（通过 Logic.startLevelByPathExternal 完成关卡装填）。
     * 选中锁住节点时报错（不会自动跳过——透明给前端，由前端决定提示语）。
     */
    public static String[] handleStart(Logic logic, LevelTree levelTree, Progress progress, int n) {
        LevelTree.StartResult r = levelTree.start(n, progress);
        switch (r.kind) {
            case EMPTY:           return ResponseBuilder.error("当前层级无可见节点");
            case LOCKED:          return ResponseBuilder.error("节点尚未解锁: " + r.lockedName);
            case ENTERED_FOLDER:  return buildBrowseResponse(levelTree);
            case STARTED_LEVEL:   return logic.startLevelByPathExternal(r.levelPath);
            default:              return ResponseBuilder.error("内部错误");
        }
    }
}