package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.Llm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReasoningEffortSupportTest {

    private static Llm llmWithOptions(String options) {
        Llm llm = new Llm();
        llm.setReasoningEffortOptions(options);
        return llm;
    }

    @Test
    void emptyOptionsDisableReasoningEffort() {
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions(null), "high")).isNull();
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions("[]"), "high")).isNull();
    }

    @Test
    void prefersMediumThenHighestBuiltin() {
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions("[\"low\",\"medium\",\"high\"]"), null))
                .isEqualTo("medium");
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions("[\"low\",\"max\",\"high\"]"), null))
                .isEqualTo("max");
    }

    @Test
    void customOnlyOptionsUseLastValue() {
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions("[\"deep\",\"ultra\"]"), null))
                .isEqualTo("ultra");
    }

    @Test
    void validSelectionWinsAndInvalidSelectionFallsBack() {
        Llm llm = llmWithOptions("[\"low\",\"medium\",\"high\"]");
        assertThat(ChatService.resolveReasoningEffort(llm, "high")).isEqualTo("high");
        assertThat(ChatService.resolveReasoningEffort(llm, "invalid")).isEqualTo("medium");
    }

    @Test
    void normalizesOptionsAndRejectsInvalidJson() {
        assertThat(LlmService.normalizeReasoningEffortOptions("[\" low \",\"high\",\"low\"]"))
                .isEqualTo("[\"low\",\"high\"]");
        assertThat(LlmService.normalizeReasoningEffortOptions("[]")).isNull();
        assertThatThrownBy(() -> LlmService.normalizeReasoningEffortOptions("[1,\"high\"]"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LlmService.normalizeReasoningEffortOptions("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
