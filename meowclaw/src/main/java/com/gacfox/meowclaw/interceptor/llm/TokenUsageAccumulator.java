package com.gacfox.meowclaw.interceptor.llm;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class TokenUsageAccumulator {
    private final AtomicLong inputTokens = new AtomicLong(0);
    private final AtomicLong outputTokens = new AtomicLong(0);
    private final AtomicLong lastPromptTokens = new AtomicLong(0);
    private volatile Consumer<Long> promptTokensListener;

    public void setPromptTokensListener(Consumer<Long> promptTokensListener) {
        this.promptTokensListener = promptTokensListener;
    }

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

    public long getLastPromptTokens() {
        return lastPromptTokens.get();
    }
}
