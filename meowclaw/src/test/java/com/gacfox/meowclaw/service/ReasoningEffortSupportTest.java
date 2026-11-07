package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.util.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReasoningEffortSupportTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonUtil jsonUtil = new JsonUtil(objectMapper);
    private final LlmService llmService = new LlmService(null, null, objectMapper, jsonUtil);

    private static Llm llmWithOptions(String options) {
        Llm llm = new Llm();
        llm.setReasoningEffortOptions(options);
        return llm;
    }

    @Test
    void emptyOptionsDisableReasoningEffort() {
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions(null), "high", jsonUtil)).isNull();
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions("[]"), "high", jsonUtil)).isNull();
    }

    @Test
    void prefersMediumThenHighestBuiltin() {
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions("[\"low\",\"medium\",\"high\"]"), null, jsonUtil))
                .isEqualTo("medium");
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions("[\"low\",\"max\",\"high\"]"), null, jsonUtil))
                .isEqualTo("max");
    }

    @Test
    void customOnlyOptionsUseLastValue() {
        assertThat(ChatService.resolveReasoningEffort(llmWithOptions("[\"deep\",\"ultra\"]"), null, jsonUtil))
                .isEqualTo("ultra");
    }

    @Test
    void validSelectionWinsAndInvalidSelectionFallsBack() {
        Llm llm = llmWithOptions("[\"low\",\"medium\",\"high\"]");
        assertThat(ChatService.resolveReasoningEffort(llm, "high", jsonUtil)).isEqualTo("high");
        assertThat(ChatService.resolveReasoningEffort(llm, "invalid", jsonUtil)).isEqualTo("medium");
    }

    @Test
    void normalizesOptionsAndRejectsInvalidJson() {
        assertThat(llmService.normalizeReasoningEffortOptions("[\" low \",\"high\",\"low\"]"))
                .isEqualTo("[\"low\",\"high\"]");
        assertThat(llmService.normalizeReasoningEffortOptions("[]")).isNull();
        assertThatThrownBy(() -> llmService.normalizeReasoningEffortOptions("[1,\"high\"]"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> llmService.normalizeReasoningEffortOptions("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
