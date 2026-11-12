package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.LlmCallLog;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.repository.LlmCallLogRepository;
import com.gacfox.meowclaw.util.JsonUtil;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.agentic.model.openai.PromptTokensDetails;
import com.gacfox.proarc.agentic.model.openai.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmCallLogServiceTest {
    private final LlmCallLogRepository repository = mock(LlmCallLogRepository.class);
    private final TraceObservationService observationService = mock(TraceObservationService.class);
    private final LlmCallLogService service = new LlmCallLogService(repository,
            new JsonUtil(new ObjectMapper()), new TraceDeletionRegistry(), observationService);

    @BeforeEach
    void setUp() {
        when(observationService.isEnabled()).thenReturn(true);
    }

    @Test
    void recordCallSkipsPersistenceWhenTraceObservationIsDisabled() {
        when(observationService.isEnabled()).thenReturn(false);

        service.recordCall(context(), "[]", ModelResponse.builder().build(), 1L, "SUCCESS", null);

        verify(repository, never()).save(any());
    }

    @Test
    void recordCallPersistsReportedCachedTokens() {
        ModelResponse response = ModelResponse.builder()
                .usage(Usage.builder()
                        .promptTokens(100)
                        .completionTokens(20)
                        .totalTokens(120)
                        .promptTokensDetails(PromptTokensDetails.builder().cachedTokens(64).build())
                        .build())
                .build();

        service.recordCall(context(), "[]", response, 1L, "SUCCESS", null);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCachedTokens()).isEqualTo(64L);
    }

    @Test
    void recordCallKeepsCachedTokensNullWhenNotReported() {
        service.recordCall(context(), "[]", ModelResponse.builder()
                .usage(Usage.builder().promptTokens(100).completionTokens(20).totalTokens(120).build())
                .build(), 1L, "SUCCESS", null);

        ArgumentCaptor<LlmCallLog> captor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCachedTokens()).isNull();
    }

    private TokenUsageContext context() {
        return new TokenUsageContext(1L, 2L, 3L, 4L, "model", "agent");
    }
}
