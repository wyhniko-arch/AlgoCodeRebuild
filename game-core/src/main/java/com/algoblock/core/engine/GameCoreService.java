package com.algoblock.core.engine;

import com.algoblock.Core;
import com.algoblock.RuntimeContext;
import com.algoblock.core.levels.Level;

/**
 * 游戏核心服务，封装 Core 引擎供 GL 调用。
 * 维护 Core 实例状态，支持多步指令累积执行。
 */
public class GameCoreService {

    private Core core;
    private int currentStep;
    private Level currentLevel;

    public GameCoreService(BlockRegistry registry) {
    }

    /**
     * 提交玩家的一行指令并返回结果。自动跟踪步骤和胜负状态。
     */
    public SubmissionResult submit(Level level, String source, long elapsedSeconds) {
        // 新关卡或首次调用：初始化
        if (core == null || currentLevel == null || currentLevel.id() != level.id()) {
            core = new Core();
            core.loadLevel(level);
            core.registerStructures();
            core.initAllowedLimits();
            currentStep = 0;
            currentLevel = level;

            // 执行初始化指令
            for (String init : level.initInsts()) {
                core.executeStatement(init, false);
            }
        }

        // 检查步数是否已耗尽
        if (currentStep >= level.stepsLimit()) {
            return SubmissionResult.rejected("步数已用完（" + level.stepsLimit() + "步限制），请重试");
        }

        // 重置判定计数
        RuntimeContext ctx = core.getRuntimeContext();
        ctx.resetCheckCounts();

        // 执行玩家指令
        boolean executed = core.executeStatement(source, true);
        if (!executed) {
            return SubmissionResult.rejected("无法解析指令: " + source);
        }

        // 执行判定指令
        for (String judge : level.judgeInsts()) {
            core.executeStatement(judge, false);
        }

        // 清空缓冲区
        ctx.clearBuffer();

        currentStep++;

        if (ctx.isWinConditionMet()) {
            return SubmissionResult.accepted("正确! 完成关卡 " + level.id(), 3);
        } else if (currentStep >= level.stepsLimit()) {
            return SubmissionResult.rejected("步数已用完（" + level.stepsLimit() + "步限制），未通过判定");
        } else {
            return SubmissionResult.rejected("未通过判定，已用 " + currentStep + "/" + level.stepsLimit() + " 步，请继续");
        }
    }
}
