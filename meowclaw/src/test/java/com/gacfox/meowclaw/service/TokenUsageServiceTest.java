package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.TokenStatsDTO;
import com.gacfox.meowclaw.dto.TokenUsageStatsRow;
import com.gacfox.meowclaw.entity.TokenUsage;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.repository.LlmRepository;
import com.gacfox.meowclaw.repository.TokenUsageRepository;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.agentic.model.openai.PromptTokensDetails;
import com.gacfox.proarc.agentic.model.openai.Usage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenUsageServiceTest {
    private final TokenUsageRepository tokenUsageRepository = mock(TokenUsageRepository.class);
    private final LlmRepository llmRepository = mock(LlmRepository.class);
    private final TokenUsageService tokenUsageService = new TokenUsageService(tokenUsageRepository, llmRepository);

    @Test
    void recordCallPreservesCachedTokensAndNullWhenNotReported() {
        TokenUsageContext context = new TokenUsageContext(1L, 2L, 3L, 4L, "model", "agent");
        tokenUsageService.recordCall(context, responseWithCachedTokens(64), "SUCCESS");
        tokenUsageService.recordCall(context, ModelResponse.builder()
                .usage(Usage.builder().promptTokens(10).completionTokens(2).totalTokens(12).build())
                .build(), "SUCCESS");

        ArgumentCaptor<TokenUsage> captor = ArgumentCaptor.forClass(TokenUsage.class);
        verify(tokenUsageRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(TokenUsage::getCachedTokens)
                .containsExactly(64L, null);
    }

    @Test
    void summaryCalculatesHitRateOnlyFromCallsReportingCachedTokens() {
        List<TokenUsageStatsRow> rows = List.of(
                row(100L, 20L, 40L),
                row(50L, 10L, 0L),
                row(1000L, 100L, null));
        when(tokenUsageRepository.findStatsRows(0L, 10L)).thenReturn(rows);
        when(llmRepository.findAll()).thenReturn(List.of());

        TokenStatsDTO.TokenStatsSummary summary = tokenUsageService.stats(0L, 10L, null).getSummary();

        assertThat(summary.getTotalCachedTokens()).isEqualTo(40L);
        assertThat(summary.getCacheHitRate()).isEqualTo(40.0 / 150.0);
        assertThat(summary.getTotalInputTokens()).isEqualTo(1150L);
    }

    @Test
    void summaryKeepsCacheMetricsNullWhenNoCallReportsThem() {
        when(tokenUsageRepository.findStatsRows(0L, 10L)).thenReturn(List.of(row(100L, 20L, null)));
        when(llmRepository.findAll()).thenReturn(List.of());

        TokenStatsDTO.TokenStatsSummary summary = tokenUsageService.stats(0L, 10L, null).getSummary();

        assertThat(summary.getTotalCachedTokens()).isNull();
        assertThat(summary.getCacheHitRate()).isNull();
    }

    private ModelResponse responseWithCachedTokens(int cachedTokens) {
        return ModelResponse.builder()
                .usage(Usage.builder()
                        .promptTokens(100)
                        .completionTokens(20)
                        .totalTokens(120)
                        .promptTokensDetails(PromptTokensDetails.builder()
                                .cachedTokens(cachedTokens)
                                .build())
                        .build())
                .build();
    }

    private TokenUsageStatsRow row(long inputTokens, long outputTokens, Long cachedTokens) {
        return new TokenUsageStatsRow(1L, "model", inputTokens, outputTokens,
                inputTokens + outputTokens, cachedTokens, 1L);
    }
}
