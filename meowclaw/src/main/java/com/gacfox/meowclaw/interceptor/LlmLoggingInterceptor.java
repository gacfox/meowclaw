package com.gacfox.meowclaw.interceptor;

import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptor;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelRequest;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.kit.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class LlmLoggingInterceptor implements LlmInterceptor {
    @Override
    public ModelResponse interceptBlocking(ModelRequest request, ModelInfo modelInfo, LlmInterceptorChain chain) {
        log.info("Llm blocking request [{}]: {}", modelInfo.getModel(), JsonUtil.dump(request));
        ModelResponse response = chain.nextBlocking(request);
        log.info("Llm blocking response [{}]: {}", modelInfo.getModel(), JsonUtil.dump(response));
        return response;
    }

    @Override
    public Flux<ModelResponse> interceptStreaming(ModelRequest request, ModelInfo modelInfo, LlmInterceptorChain chain) {
        log.info("Llm streaming request [{}]: {}", modelInfo.getModel(), JsonUtil.dump(request));
        return chain.nextStreaming(request)
                .doOnComplete(() -> log.info("Llm stream complete [{}]", modelInfo.getModel()))
                .doOnError(e -> log.error("Llm stream error [{}]", modelInfo.getModel(), e));
    }
}
