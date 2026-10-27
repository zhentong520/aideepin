package com.moyz.adi.common.languagemodel.data;

import com.moyz.adi.common.config.AdiProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 工具调用循环三重预算（T6-10）：深度（次数）+ 墙钟时间 + token 累计。
 * <p>
 * 三个预算同时生效、任一超限即中止循环；中止时记录具体是哪个预算超限
 * （abortedReason），避免"循环退出但不知道为什么"。
 * <p>
 * 配置：adi.agent.tool-loop-max-depth / -max-seconds / -max-tokens（默认 5 / 300 / 200000，
 * 深度默认值与历史硬编码 MAX_TOOL_CALL_DEPTH=5 一致）。
 * 无 Spring 上下文（单测）时按默认值构造。
 * <p>
 * Triple budget for the tool-call loop (T6-10): depth + wall-clock time + accumulated
 * tokens. Any exceeded budget aborts the loop, recording which one tripped.
 */
@Slf4j
public class ToolLoopBudget {

    public static final String REASON_DEPTH = "DEPTH";
    public static final String REASON_WALL_CLOCK = "WALL_CLOCK";
    public static final String REASON_TOKENS = "TOKENS";

    /** 启动期推送（ModelStaticConfigInitializer）；null 时 fromConfig() 走默认值 */
    private static volatile AdiProperties configProps;

    public static void initConfig(AdiProperties props) {
        configProps = props;
    }

    @Getter
    private final int maxDepth;
    @Getter
    private final long maxWallMs;
    @Getter
    private final long maxTotalTokens;

    private final long startedAtMs;

    @Getter
    private int depthUsed = 0;
    @Getter
    private long tokensUsed = 0;
    @Getter
    private String abortedReason;

    public ToolLoopBudget(int maxDepth, long maxWallMs, long maxTotalTokens) {
        this.maxDepth = maxDepth;
        this.maxWallMs = maxWallMs;
        this.maxTotalTokens = maxTotalTokens;
        this.startedAtMs = System.currentTimeMillis();
    }

    /**
     * 从 AdiProperties 读取配置构造（adi.agent.tool-loop-*）。
     * 无 Spring 上下文时回退默认值（5 / 300s / 200000）。
     */
    public static ToolLoopBudget fromConfig() {
        int depth = 5;
        long seconds = 300;
        long tokens = 200000;
        AdiProperties props = configProps;
        if (null != props) {
            depth = props.getAgent().getToolLoopMaxDepth();
            seconds = props.getAgent().getToolLoopMaxSeconds();
            tokens = props.getAgent().getToolLoopMaxTokens();
        }
        return new ToolLoopBudget(depth, seconds * 1000L, tokens);
    }

    /**
     * 累计 token（LLM 调用返回的 input+output 之和，含中间工具调用步骤）。
     * 返回累计后的总量。
     */
    public synchronized long accumulateTokens(long tokens) {
        if (tokens > 0) {
            tokensUsed += tokens;
        }
        return tokensUsed;
    }

    /**
     * 检查时间 / token 两个预算是否超限。超限返回原因（WALL_CLOCK / TOKENS），未超限返回 empty。
     * 深度预算走 {@link #consumeDepth()}。首次超限后固定返回同一原因（中止原因不漂移）。
     */
    public synchronized Optional<String> checkExceeded() {
        if (null != abortedReason) {
            return Optional.of(abortedReason);
        }
        if (tokensUsed > maxTotalTokens) {
            abortedReason = REASON_TOKENS;
        } else if (System.currentTimeMillis() - startedAtMs > maxWallMs) {
            abortedReason = REASON_WALL_CLOCK;
        }
        return Optional.ofNullable(abortedReason);
    }

    /**
     * 消耗一轮深度额度。返回 false 表示深度预算已耗尽（并记录中止原因）。
     */
    public synchronized boolean consumeDepth() {
        if (depthUsed >= maxDepth) {
            if (null == abortedReason) {
                abortedReason = REASON_DEPTH;
            }
            return false;
        }
        depthUsed++;
        return true;
    }
}
