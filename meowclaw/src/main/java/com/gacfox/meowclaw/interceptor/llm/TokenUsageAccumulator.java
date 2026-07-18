package com.gacfox.meowclaw.interceptor.llm;

import java.util.concurrent.atomic.AtomicLong;

public class TokenUsageAccumulator {
    private final AtomicLong inputTokens = new AtomicLong(0);
    private final AtomicLong outputTokens = new AtomicLong(0);
    private final AtomicLong lastPromptTokens = new AtomicLong(0);

    public void addInput(int tokens) {
        inputTokens.addAndGet(tokens);
        lastPromptTokens.set(tokens);
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
