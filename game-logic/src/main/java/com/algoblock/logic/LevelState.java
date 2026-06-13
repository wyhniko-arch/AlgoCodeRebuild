package com.algoblock.logic;

import com.algoblock.context.RuntimeContext;
import com.algoblock.lang.CommandDefinition;
import com.algoblock.structure.Abstract;
import com.algoblock.tools.jsonloader.model.LevelConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关卡级字段容器：将一关游戏期间所有可变状态集中到此对象。
 *
 * 设计动机：
 *   将原本散落在 Logic 单例上的关卡字段聚合，使后续模块（Loader/Executor/Lexer 等）
 *   只需接收一个 LevelState 引用即可读写关卡数据，Logic 自身不再持有这些字段。
 *
 * 生命周期：与一关游戏同生命周期。
 *   - 进入关卡时由 Logic 创建并交给 LevelLoader 装填
 *   - 退出关卡（reset / win / fail）时 Logic 调用 clear() 并将引用置 null
 *
 * 字段语义保留原 Logic 中的含义，未做任何业务变更。
 */
public class LevelState {

    // ==========================================
    // 运行时核心引用
    // ==========================================

    /** 运行时上下文，承载游戏对象、缓冲区、引擎回调等。 */
    public RuntimeContext runtimeContext;

    /** 关卡 JSON 解析后的配置。 */
    public LevelConfig levelConfig;

    /** 当前正在玩的关卡完整路径，如 "tutorial/basics/step01"。 */
    public String currentLevelPath;

    // ==========================================
    // 三张指令索引表（注册阶段填充）
    // ==========================================

    /** structId → Template 实例。 */
    public Map<String, Abstract> structidToStructure = new HashMap<>();

    /** structId_commandId → CommandDefinition，O(1) 快速定位。 */
    public Map<String, CommandDefinition> struct_command_idToCommand = new HashMap<>();

    /** structId → CommandDefinition 列表（含 argHints / tags）。 */
    public Map<String, List<CommandDefinition>> struct_idToCommands = new HashMap<>();

    /**
     * 标签 → 该标签下所有有效 CommandDefinition 的集合。
     * 在 initAllowedLimits 结束后构建，供 ArgHintResolver 的 cmd[tag] 使用。
     */
    public Map<String, List<CommandDefinition>> commandsByTag = new HashMap<>();

    // ==========================================
    // 关卡内交互状态
    // ==========================================

    /** 玩家正在输入的语句缓冲。 */
    public StringBuilder inputBuffer = new StringBuilder();

    /** 当前补全列表中高亮选项的索引（被 tab 选中的那个）。 */
    public int selectedOptionIndex = 0;

    /** 已经消耗的回合数（玩家提交了成功执行语句的次数）。 */
    public int currentStep = 0;

    // ==========================================
    // 重置
    // ==========================================

    /**
     * 清空全部关卡级状态。供 Logic 在退出关卡时调用。
     * 引用集合不置 null，而是清空内容——避免 NullPointerException 的同时复用容器。
     */
    public void clear() {
        runtimeContext             = null;
        levelConfig                = null;
        currentLevelPath           = null;
        structidToStructure.clear();
        struct_command_idToCommand.clear();
        struct_idToCommands.clear();
        commandsByTag.clear();
        inputBuffer.setLength(0);
        selectedOptionIndex = 0;
        currentStep         = 0;
    }
}