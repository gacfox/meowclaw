package com.gacfox.meowclaw.interceptor.llm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageAccumulatorTest {

    @Test
    void notifiesPromptTokensListenerOnEveryLlmResponse() {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();
        AtomicLong latest = new AtomicLong();
        accumulator.setPromptTokensListener(latest::set);

        accumulator.addInput(1200);
        accumulator.addInput(2400);

        assertThat(latest.get()).isEqualTo(2400L);
        assertThat(accumulator.getLastPromptTokens()).isEqualTo(2400L);
        assertThat(accumulator.getInputTokens()).isEqualTo(3600L);
    }
}
