package com.gacfox.meowclaw.interceptor.llm;

import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class TokenUsageAccumulator {
    private final AtomicLong inputTokens = new AtomicLong(0);
    private final AtomicLong outputTokens = new AtomicLong(0);
    private final AtomicLong lastPromptTokens = new AtomicLong(0);
    private final AtomicLong speedOutputTokens = new AtomicLong(0);
    private final AtomicLong speedDurationMs = new AtomicLong(0);
    @Setter
    private volatile Consumer<Long> promptTokensListener;

    public void addInput(int tokens) {
        inputTokens.addAndGet(tokens);
        lastPromptTokens.set(tokens);
        if (promptTokensListener != null) {
            promptTokensListener.accept((long) tokens);
        }
    }

    public void addOutput(int tokens) {
        outputTokens.addAndGet(tokens);
    }

    public long getInputTokens() {
        return inputTokens.get();
    }

    public long getOutputTokens() {
        return outputTokens.get();
    }

    /**
     * 累计一次可用于输出速度计算的调用；零输出调用仍计入总耗时
     */
    public void addOutputSpeedSample(long tokens, long durationMs) {
        if (tokens < 0 || durationMs <= 0) {
            return;
        }
        speedOutputTokens.addAndGet(tokens);
        speedDurationMs.addAndGet(durationMs);
    }

    /**
     * 返回按 LLM 调用耗时加权的平均输出速度，无有效样本时返回 null
     */
    public Double getOutputTokensPerSecond() {
        long tokens = speedOutputTokens.get();
        long durationMs = speedDurationMs.get();
        if (tokens <= 0 || durationMs <= 0) {
            return null;
        }
        return tokens * 1000.0 / durationMs;
    }

    public long getLastPromptTokens() {
        return lastPromptTokens.get();
    }
}
