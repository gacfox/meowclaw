package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.LlmCallLog;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.repository.LlmCallLogRepository;
import com.gacfox.meowclaw.util.JsonUtil;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.agentic.model.openai.Usage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmCallLogService {
    private final LlmCallLogRepository llmCallLogRepository;
    private final JsonUtil jsonUtil;
    private final TraceDeletionRegistry traceDeletionRegistry;
    private final TraceObservationService traceObservationService;

    @Autowired
    public LlmCallLogService(LlmCallLogRepository llmCallLogRepository, JsonUtil jsonUtil,
                             TraceDeletionRegistry traceDeletionRegistry,
                             TraceObservationService traceObservationService) {
        this.llmCallLogRepository = llmCallLogRepository;
        this.jsonUtil = jsonUtil;
        this.traceDeletionRegistry = traceDeletionRegistry;
        this.traceObservationService = traceObservationService;
    }

    /**
     * 记录一次LLM调用的可清理追踪明细
     */
    @Transactional
    public void recordCall(TokenUsageContext ctx, String requestMessages, ModelResponse response,
                           long durationMs, String status, String errorMessage) {
        if (!traceObservationService.isEnabled()) {
            return;
        }
        Usage usage = response != null ? response.getUsage() : null;
        long input = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0L;
        long output = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0L;
        long total = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : input + output;

        LlmCallLog log = new LlmCallLog();
        log.setLlmId(ctx.llmId());
        log.setAgentId(ctx.agentId());
        log.setConversationId(ctx.conversationId());
        log.setBatchId(ctx.batchId());
        log.setModel(ctx.model());
        log.setPurpose(ctx.purpose());
        log.setRequestMessages(requestMessages);
        if (response != null) {
            log.setResponseContent(response.extractBlockingContent());
            log.setReasoningContent(response.extractBlockingReasoningContent());
            var toolCalls = response.extractBlockingToolCalls();
            if (toolCalls != null && !toolCalls.isEmpty()) {
                try {
                    log.setResponseToolCalls(jsonUtil.dump(toolCalls));
                } catch (Exception ignored) {
                }
            }
        }
        log.setInputTokens(input);
        log.setOutputTokens(output);
        log.setTotalTokens(total);
        log.setCachedTokens(usage != null && usage.getCachedTokens() != null
                ? usage.getCachedTokens().longValue() : null);
        log.setDurationMs(durationMs);
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        log.setCreatedAt(System.currentTimeMillis());
        traceDeletionRegistry.writeIfNotDeleted(ctx.batchId(), () -> llmCallLogRepository.save(log));
    }
}
