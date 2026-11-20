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

    @Test
    void calculatesDurationWeightedOutputSpeed() {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();

        accumulator.addOutputSpeedSample(100, 2000);
        accumulator.addOutputSpeedSample(10, 10000);

        assertThat(accumulator.getOutputTokensPerSecond()).isEqualTo(110000.0 / 12000);
    }

    @Test
    void ignoresInvalidSamplesAndIncludesZeroOutputCallDuration() {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();
        accumulator.addOutputSpeedSample(-1, 1000);
        accumulator.addOutputSpeedSample(100, 0);
        assertThat(accumulator.getOutputTokensPerSecond()).isNull();

        accumulator.addOutputSpeedSample(0, 2000);
        accumulator.addOutputSpeedSample(20, 2000);
        assertThat(accumulator.getOutputTokensPerSecond()).isEqualTo(5.0);
    }

    @Test
    void returnsNullWhenEveryCallHasZeroOutputTokens() {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();

        accumulator.addOutputSpeedSample(0, 2000);

        assertThat(accumulator.getOutputTokensPerSecond()).isNull();
    }
}
