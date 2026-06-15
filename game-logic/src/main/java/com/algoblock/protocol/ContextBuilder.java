package com.algoblock.protocol;

import com.algoblock.lang.CommandDefinition;
import com.algoblock.logic.LevelState;
import com.algoblock.tools.aidescription.AiDescription;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 装配 query:aiContext 返回的文本。
 *
 * 输出是单段 Markdown 字符串，由 ResponseBuilder.aiContext() 包成 JSON 后返回。
 * 拼接顺序：
 *   1. 游戏总规则（硬编码在 GAME_RULES 静态字段）
 *   2. 各结构特性（来自 AiDescription，按当前关卡实际注册的结构列出）
 *   3. 初始关卡状态（story / inputDesc / outputDesc / aiHint，任一非空才出现）
 *   4. 当前局面（当前对象状态 + buffer 配置 + 步数）
 *   5. 玩家仍可用的指令清单（含剩余次数与描述）
 *
 * 任何段为空都自然忽略（不会出现空标题）。
 *
 * 设计原则：
 *   - 后端只产出"事实"文本，不附加任何 prompt 工程指令（"请用 JSON 回答..."这类）
 *   - 不暴露 judge_commands（避免泄露答案）
 *   - 描述文本完全数据驱动（AiDescription 从 JSON 加载）
 */
public final class ContextBuilder {

    private ContextBuilder() {}

    /**
     * 游戏总规则——硬编码文本块。
     * 这段不随关卡变化，是 AI 理解整个游戏前置必读。
     */
    private static final String GAME_RULES =
        "# 游戏规则\n" +
        "\n" +
        "你正在协助玩家通关一款算法解谜游戏。游戏目标：使用关卡允许的指令操作若干数据结构对象，" +
        "将它们从初始状态变换到目标状态。\n" +
        "\n" +
        "核心规则：\n" +
        "- 每条玩家指令的使用次数有上限，用完后该指令禁用。\n" +
        "- 关卡设有总步数上限，玩家提交一条成功执行的语句计一步。步数耗尽未达目标判负。\n" +
        "- buffer（运行时缓冲区）是结构之间传值的中转通道：\n" +
        "  · buffer_out 类指令（如 Queue.pop / Stack.pop / PQ.pop）会把弹出的值推入缓冲区。\n" +
        "  · buffer_in 类指令（如 Queue.add / Stack.push / PQ.push）会从缓冲区取一个值放入结构。\n" +
        "  · 关卡的 buffer.command_in 和 buffer.command_out 设置了连锁规则——玩家执行一条 out 指令时，\n" +
        "    引擎会自动接一条 command_in 指令；反之亦然。这能让玩家用单条指令实现跨结构搬运。\n" +
        "- 语句语法以 [ ] 为字面层级标识、( ) 为参数定界。例：Queue[(A)].pop 表示对名为 A 的队列执行 pop。\n" +
        "\n" +
        "你的任务：根据下方信息，分析玩家当前的局面，给出能推进玩家达成目标的下一步建议。" +
        "若推理后认为局面已无法在剩余步数内达成目标，请明确告知玩家此关已无解，避免在错误方向继续徘徊。";

    /**
     * 装配并返回完整文本。
     */
    public static String build(LevelState state) {
        // 预加载——若关卡装填阶段已调过这就是 no-op
        AiDescription.ensureLoaded(state.structidToStructure.keySet());

        StringBuilder sb = new StringBuilder();
        appendSection(sb, GAME_RULES);
        appendSection(sb, sectionStructures(state));
        appendSection(sb, sectionInitialState(state));
        appendSection(sb, sectionCurrentState(state));
        appendSection(sb, sectionPlayerCapability(state));
        return sb.toString().trim();
    }

    /** 追加一个段落，前后保证空行；段落为空时整段忽略。 */
    private static void appendSection(StringBuilder sb, String section) {
        if (section == null) return;
        String trimmed = section.trim();
        if (trimmed.isEmpty()) return;
        if (sb.length() > 0) sb.append("\n\n");
        sb.append(trimmed);
    }

    // ==========================================
    // 段 2：结构特性
    // ==========================================

    private static String sectionStructures(LevelState state) {
        if (state.structidToStructure.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("# 当前可用的数据结构\n");
        boolean any = false;
        for (String structId : state.structidToStructure.keySet()) {
            String desc = AiDescription.forStructure(structId);
            if (desc.isEmpty()) continue; // 没文案就跳过
            sb.append("\n## ").append(structId).append("\n");
            sb.append(desc).append("\n");
            any = true;
        }
        return any ? sb.toString() : "";
    }

    // ==========================================
    // 段 3：初始关卡状态
    // ==========================================

    private static String sectionInitialState(LevelState state) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;

        if (notBlank(state.levelConfig.story)) {
            sb.append("剧情：").append(state.levelConfig.story.trim()).append("\n");
            any = true;
        }
        if (notBlank(state.levelConfig.inputDesc)) {
            sb.append("初始状态：").append(state.levelConfig.inputDesc.trim()).append("\n");
            any = true;
        }
        if (notBlank(state.levelConfig.outputDesc)) {
            sb.append("目标状态：").append(state.levelConfig.outputDesc.trim()).append("\n");
            any = true;
        }
        if (notBlank(state.levelConfig.aiHint)) {
            // 关卡作者留给 AI 的私有提示——玩家看不到
            sb.append("关卡作者提示（玩家不可见）：").append(state.levelConfig.aiHint.trim()).append("\n");
            any = true;
        }

        return any ? ("# 本关任务\n" + sb.toString()) : "";
    }

    // ==========================================
    // 段 4：当前局面
    // ==========================================

    private static String sectionCurrentState(LevelState state) {
        StringBuilder sb = new StringBuilder("# 当前局面\n");

        // 步数
        int used = state.currentStep;
        int max  = state.levelConfig.stepsLimit;
        sb.append("已用步数：").append(used).append(" / ").append(max)
          .append("（剩余 ").append(max - used).append("）\n\n");

        // buffer 配置
        String cmdIn  = state.runtimeContext.getBufferCommandIn();
        String cmdOut = state.runtimeContext.getBufferCommandOut();
        if (notBlank(cmdIn) || notBlank(cmdOut)) {
            sb.append("缓冲区连锁规则：\n");
            if (notBlank(cmdIn))  sb.append("  · 玩家执行 out 指令后，自动追加：").append(cmdIn).append("\n");
            if (notBlank(cmdOut)) sb.append("  · 玩家执行 in 指令前，自动前置：").append(cmdOut).append("\n");
            sb.append("\n");
        }

        // 对象快照
        List<JsonObject> snapshots = state.runtimeContext.collectAllSnapshots(state.structidToStructure);
        if (!snapshots.isEmpty()) {
            sb.append("现存游戏对象：\n");
            for (JsonObject snap : snapshots) {
                String structId = snap.get("structId").getAsString();
                String name     = snap.get("name").getAsString();
                JsonObject st   = snap.getAsJsonObject("state");
                sb.append("  · ").append(structId).append("[(").append(name).append(")]：")
                  .append(st.toString()).append("\n");
            }
        } else {
            sb.append("当前没有游戏对象。\n");
        }

        return sb.toString();
    }

    // ==========================================
    // 段 5：玩家仍可用的指令清单
    // ==========================================

    private static String sectionPlayerCapability(LevelState state) {
        List<CommandDefinition> usable = new ArrayList<>();
        for (List<CommandDefinition> defs : state.struct_idToCommands.values()) {
            for (CommandDefinition def : defs) {
                if (def.getMaxUses() <= 0) continue; // 玩家不可见
                usable.add(def);
            }
        }
        if (usable.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("# 玩家可用的指令\n");
        for (CommandDefinition def : usable) {
            int remaining = def.getMaxUses() - def.getUsedCount();
            String exhausted = remaining <= 0 ? "（已用完）" : ("（剩余 " + remaining + " / " + def.getMaxUses() + " 次）");
            sb.append("\n- `").append(def.getPattern()).append("` ").append(exhausted).append("\n");

            String desc = AiDescription.forMethod(def.getStructId(), def.getCommandId());
            if (!desc.isEmpty()) {
                sb.append("    ").append(desc).append("\n");
            }

            String[] hints = def.getArgHints();
            if (hints != null && hints.length > 0) {
                sb.append("    参数语义：");
                for (int i = 0; i < hints.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("@").append(i + 1).append("=").append(hints[i]);
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // ==========================================
    // 工具
    // ==========================================

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}