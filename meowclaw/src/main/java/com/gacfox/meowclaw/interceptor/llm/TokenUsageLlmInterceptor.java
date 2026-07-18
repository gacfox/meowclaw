package com.gacfox.meowclaw.interceptor.llm;

import com.gacfox.meowclaw.service.TokenUsageLogService;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptor;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelRequest;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.agentic.model.openai.Usage;

/**
 * Tokens消耗拦截器：既累加当前批次的Tokens总量（供批次汇总），
 * 又把每次LLM调用的明细落库（供Tokens统计报表）。
 */
public class TokenUsageLlmInterceptor implements LlmInterceptor {
    private final TokenUsageAccumulator accumulator;
    private final TokenUsageLogService logService;
    private final TokenUsageContext context;

    public TokenUsageLlmInterceptor(TokenUsageAccumulator accumulator,
                                    TokenUsageLogService logService,
                                    TokenUsageContext context) {
        this.accumulator = accumulator;
        this.logService = logService;
        this.context = context;
    }

    @Override
    public ModelResponse interceptBlocking(ModelRequest request, ModelInfo modelInfo, LlmInterceptorChain chain) {
        ModelResponse response = chain.nextBlocking(request);
        Usage usage = response.getUsage();
        if (usage != null) {
            if (usage.getPromptTokens() != null) accumulator.addInput(usage.getPromptTokens());
            if (usage.getCompletionTokens() != null) accumulator.addOutput(usage.getCompletionTokens());
            logService.record(context, usage);
        }
        return response;
    }

}
