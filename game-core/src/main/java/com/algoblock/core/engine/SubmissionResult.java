package com.algoblock.core.engine;

/**
 * 玩家提交执行后的结果。
 */
public record SubmissionResult(boolean accepted, String message, Score score) {

    public record Score(int stars) {
    }

    public static SubmissionResult accepted(String message, int stars) {
        return new SubmissionResult(true, message, new Score(stars));
    }

    public static SubmissionResult rejected(String message) {
        return new SubmissionResult(false, message, new Score(0));
    }
}
