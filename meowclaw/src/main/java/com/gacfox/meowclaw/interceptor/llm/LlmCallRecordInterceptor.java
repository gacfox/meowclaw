package com.gacfox.meowclaw.interceptor.llm;

import com.gacfox.meowclaw.service.LlmCallLogService;
import com.gacfox.meowclaw.service.TokenUsageService;
import com.gacfox.meowclaw.util.JsonUtil;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptor;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelRequest;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.agentic.model.openai.Usage;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM调用记录拦截器：分别记录可清理的追踪明细和独立Token统计，
 * 同时累加批次tokens（供上下文水位计算）。
 * 位于重试拦截器外层，一次逻辑调用（含重试）只记一条记录。
 */
@Slf4j
public class LlmCallRecordInterceptor implements LlmInterceptor {
    private static final int MAX_REQUEST_MESSAGES_CHARS = 256 * 1024;

    private final TokenUsageAccumulator accumulator;
    private final LlmCallLogService logService;
    private final TokenUsageService tokenUsageService;
    private final TokenUsageContext context;
    private final JsonUtil jsonUtil;

    public LlmCallRecordInterceptor(TokenUsageAccumulator accumulator, LlmCallLogService logService,
                                    TokenUsageService tokenUsageService, TokenUsageContext context, JsonUtil jsonUtil) {
        this.accumulator = accumulator;
        this.logService = logService;
        this.tokenUsageService = tokenUsageService;
        this.context = context;
        this.jsonUtil = jsonUtil;
    }

    @Override
    public ModelResponse interceptBlocking(ModelRequest request, ModelInfo modelInfo, LlmInterceptorChain chain) {
        long startNanos = System.nanoTime();
        try {
            ModelResponse response = chain.nextBlocking(request);
            record(request, response, elapsedMillis(startNanos), "SUCCESS", null);
            return response;
        } catch (Exception e) {
            record(request, null, elapsedMillis(startNanos), "ERROR", e.getMessage());
            throw e;
        }
    }

    @Override
    public Flux<ModelResponse> interceptStreaming(ModelRequest request, ModelInfo modelInfo, LlmInterceptorChain chain) {
        long startNanos = System.nanoTime();
        List<ModelResponse> chunks = new ArrayList<>();
        return chain.nextStreaming(request)
                .doOnNext(chunks::add)
                .doOnComplete(() -> record(request, ModelResponse.mergeStreamChunks(chunks),
                        elapsedMillis(startNanos), "SUCCESS", null))
                .doOnError(e -> {
                    ModelResponse partial = chunks.isEmpty() ? null : ModelResponse.mergeStreamChunks(chunks);
                    record(request, partial, elapsedMillis(startNanos), "ERROR", e.getMessage());
                });
    }

    private void record(ModelRequest request, ModelResponse response, long durationMs,
                        String status, String errorMessage) {
        Usage usage = response != null ? response.getUsage() : null;
        if (usage != null) {
            if (usage.getPromptTokens() != null) accumulator.addInput(usage.getPromptTokens());
            if (usage.getCompletionTokens() != null) accumulator.addOutput(usage.getCompletionTokens());
        }
        String requestMessages = serializeMessages(request);
        Schedulers.boundedElastic().schedule(() -> {
            try {
                logService.recordCall(context, requestMessages, response, durationMs, status, errorMessage);
            } catch (Exception e) {
                log.warn("LLM追踪记录落库失败: {}", e.getMessage());
            }
            try {
                tokenUsageService.recordCall(context, response, status);
            } catch (Exception e) {
                log.warn("Token统计落库失败: {}", e.getMessage());
            }
        });
    }

    private String serializeMessages(ModelRequest request) {
        try {
            String json = jsonUtil.dump(request.getMessages());
            if (json.length() > MAX_REQUEST_MESSAGES_CHARS) {
                return json.substring(0, MAX_REQUEST_MESSAGES_CHARS) + "...[因长度截断]";
            }
            return json;
        } catch (Exception e) {
            return null;
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
