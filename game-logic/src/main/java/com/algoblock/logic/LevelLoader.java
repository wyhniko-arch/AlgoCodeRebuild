package com.algoblock.logic;

import com.algoblock.lex.CommandDefinition;
import com.algoblock.lex.StatementExecutor;
import com.algoblock.structure.Abstract;
import com.algoblock.structure.StructureMethod;
import com.algoblock.tools.buffer.RowBuffer;
import com.algoblock.tools.jsonloader.analysis.LevelConfigLoader;
import com.algoblock.tools.jsonloader.analysis.StructureRegistryLoader;
import com.algoblock.tools.jsonloader.namerule.LevelConfig;
import com.algoblock.tools.jsonloader.namerule.StructureRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 关卡装填器：把一关从"空 LevelState"装填到"可游玩"。
 *
 * 设计为无状态静态类——所有数据通过 LevelState 出入，便于单测和重用。
 *
 * 装填顺序固定为：
 *   1. loadLevel           解析关卡 JSON，挂上 RuntimeContext 的缓冲区配置
 *   2. registerStructures  实例化关卡声明的所有结构 Template，挂载预设指令
 *   3. initAllowedLimits   按关卡授权清单更新配额 / 动态拉取未挂载的指令
 *   4. buildCommandsByTag  按 tag 建立 CommandDefinition 索引
 *   5. executeInitCommands 执行关卡 init_commands（生成初始游戏对象）
 *
 * 判分阶段在 enter 提交时调用，executeJudgeCommands 也放在这里供 InputHandler 调用。
 */
public final class LevelLoader {

    private LevelLoader() {}

    // ==========================================
    // 1. 解析关卡 JSON
    // ==========================================

    /**
     * 从外部加载器获取关卡配置并把 buffer 段挂到 RuntimeContext。
     */
    public static void loadLevel(LevelState state, String levelPath) {
        state.levelConfig = LevelConfigLoader.getConfig(levelPath);
        if (state.levelConfig.buffer != null) {
            state.runtimeContext.setBufferConfig(
                    state.levelConfig.buffer.commandIn,
                    state.levelConfig.buffer.commandOut);
        }
    }

    // ==========================================
    // 2. 注册结构 + 挂载预设指令
    // ==========================================

    /**
     * 遍历关卡声明的结构 ID，反射实例化对应 Template，
     * 将每个 Template 默认加载的方法包装为 CommandDefinition 写入索引。
     * 此阶段所有 def 的 maxUses 默认为 0（玩家无权限）；权限在 initAllowedLimits 中开放。
     */
    public static void registerStructures(LevelState state) {
        RowBuffer.append("\n[Debug] === 阶段一: 加载物理结构与挂载基础指令 ===");
        StructureRegistry registryConfig = StructureRegistryLoader.getRegistry();

        for (String structId : state.levelConfig.structUsed) {
            String fqcn = registryConfig.getFQCN(structId);
            if (fqcn == null) {
                throw new RuntimeException("配置错误：在注册表实例中未找到结构体 [" + structId + "] 的定义");
            }

            try {
                Abstract structInstance = (Abstract) Class.forName(fqcn)
                        .getDeclaredConstructor().newInstance(); // 新建结构实例
                state.structidToStructure.put(structId, structInstance);
                state.struct_idToCommands.put(structId, new ArrayList<>());

                // 遍历该 Template 默认已加载的方法，构建 CommandDefinition
                for (Map.Entry<String, StructureMethod> entry : structInstance.getLoadedMethods().entrySet()) {
                    CommandDefinition newDef = new CommandDefinition(
                            structId,
                            entry.getKey(),                       // commandId
                            entry.getValue().getPattern(),
                            entry.getValue().getArgHints(),
                            entry.getValue().getTags());
                    newDef.setMaxUses(0); // 默认玩家无使用次数
                    state.struct_command_idToCommand.put(structId + "_" + entry.getKey(), newDef);
                    state.struct_idToCommands.get(structId).add(newDef);
                    RowBuffer.append("[Debug] -> [" + structId + "] 成功挂载预设指令: "
                            + entry.getKey() + " | Pattern: " + entry.getValue().getPattern());
                }
            } catch (Exception e) {
                RowBuffer.append("[Debug] [异常] 结构体注册严重失败: " + fqcn);
            }
        }
    }

    // ==========================================
    // 3. 应用授权清单（开放配额 / 动态拉取新指令）
    // ==========================================

    /**
     * 处理 levelConfig.commandsAllowed：
     *   - 若指令已在预设缓存中，仅更新 maxUses
     *   - 否则触发 Template 的 ifLoadMethodDynamically 反射加载，
     *     成功后用 5 参完整构造 CommandDefinition 写入索引（带 argHints/tags）
     */
    public static void initAllowedLimits(LevelState state) {
        RowBuffer.append("\n[Debug] === 阶段二: 遍历授权名单更新权限 / 动态拉取新指令 ===");
        for (LevelConfig.CommandConfig commandAllowed : state.levelConfig.commandsAllowed) {
            String struct_command_id = commandAllowed.structId + "_" + commandAllowed.commandId;
            RowBuffer.append("[Debug] 处理授权清单: 结构[" + commandAllowed.structId
                    + "] - 指令[" + commandAllowed.commandId
                    + "] -> 授权配额: " + commandAllowed.maxUses);

            CommandDefinition oldDef = state.struct_command_idToCommand.get(struct_command_id);
            RowBuffer.append("[Debug]   |- 预设缓存匹配情况: " + (oldDef != null));

            if (oldDef != null) {
                oldDef.setMaxUses(commandAllowed.maxUses);
                RowBuffer.append("[Debug]   |- [完毕] 仅更新配额上限");
            } else {
                RowBuffer.append("[Debug]   |- 触发向下层架构反射请求");
                Abstract structTemplate = state.structidToStructure.get(commandAllowed.structId);
                if (structTemplate != null) {
                    boolean loaded = structTemplate.ifLoadMethodDynamically(commandAllowed.commandId);
                    RowBuffer.append("[Debug]   |- 底层架构反馈加载结果: " + loaded);
                    if (loaded) {
                        // 从动态加载后的 StructureMethod 实例拿完整的 pattern / argHints / tags
                        StructureMethod sm = structTemplate.getLoadedMethods().get(commandAllowed.commandId);
                        CommandDefinition newDef = new CommandDefinition(
                                commandAllowed.structId,
                                commandAllowed.commandId,
                                sm.getPattern(),
                                sm.getArgHints(),
                                sm.getTags());
                        newDef.setMaxUses(commandAllowed.maxUses);
                        state.struct_command_idToCommand.put(struct_command_id, newDef);
                        state.struct_idToCommands
                                .computeIfAbsent(commandAllowed.structId, k -> new ArrayList<>())
                                .add(newDef);
                        RowBuffer.append("[Debug]   |- [完毕] 指令已动态组装加入引擎库. Pattern: " + sm.getPattern());
                    }
                }
            }
        }
    }

    // ==========================================
    // 4. 按 tag 建立 CommandDefinition 索引
    // ==========================================

    /**
     * 在 initAllowedLimits 之后调用：
     * 收集所有 maxUses>0 的 CommandDefinition 按 tag 分桶，供 ArgHintResolver 的 cmd[tag] 使用。
     * 注意是视图索引——指向同一批 CommandDefinition 对象，无独立副本。
     */
    public static void buildCommandsByTag(LevelState state) {
        state.commandsByTag.clear();
        for (List<CommandDefinition> defs : state.struct_idToCommands.values()) {
            for (CommandDefinition def : defs) {
                if (def.getMaxUses() <= 0) continue; // 无权限的不加入索引
                for (String tag : def.getTags()) {
                    state.commandsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(def);
                }
            }
        }
        RowBuffer.append("[Debug] commandsByTag 构建完成，标签数: " + state.commandsByTag.size());
    }

    // ==========================================
    // 5. 关卡指令的初始化阶段 / 判分阶段
    // ==========================================

    /** 顺序执行 init_commands，由系统而非玩家身份执行。 */
    public static void executeInitCommands(LevelState state) {
        RowBuffer.append("\n--- 初始化阶段 ---");
        for (String initCommand : state.levelConfig.initCommands) {
            StatementExecutor.execute(state, initCommand, false);
        }
    }

    /** 顺序执行 judge_commands，由系统身份执行。每次玩家提交语句后调用一次。 */
    public static void executeJudgeCommands(LevelState state) {
        RowBuffer.append("\n--- 判分阶段 ---");
        for (String judge : state.levelConfig.judgeCommands) {
            StatementExecutor.execute(state, judge, false);
        }
    }
}