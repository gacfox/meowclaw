package com.gacfox.meowclaw.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.util.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceReasoningEffortTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonUtil jsonUtil = new JsonUtil(objectMapper);

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
}
