package com.gacfox.meowclaw.service.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.util.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmServiceReasoningEffortTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonUtil jsonUtil = new JsonUtil(objectMapper);
    private final LlmService llmService = new LlmService(null, null, objectMapper, jsonUtil);

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
