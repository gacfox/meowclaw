package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gacfox.meowclaw.entity.EmbeddingModel;
import com.gacfox.meowclaw.repository.EmbeddingModelRepository;
import com.gacfox.proarc.agentic.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * 向量嵌入模型调用服务
 */
@Slf4j
@Service
public class EmbeddingService {

    private final EmbeddingModelRepository embeddingModelRepository;
    private final HttpClient httpClient;

    @Autowired
    public EmbeddingService(EmbeddingModelRepository embeddingModelRepository, HttpClient httpClient) {
        this.embeddingModelRepository = embeddingModelRepository;
        this.httpClient = httpClient;
    }

    /**
     * 调用向量嵌入模型获取向量列表
     *
     * @param embeddingModelId 模型配置ID
     * @param inputs           待嵌入文本列表
     * @return 与输入顺序对应的向量列表
     */
    public List<List<Double>> embed(Long embeddingModelId, List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        EmbeddingModel model = embeddingModelRepository.findById(embeddingModelId)
                .orElseThrow(() -> new IllegalArgumentException("嵌入模型配置不存在"));

        EmbeddingRequest request = new EmbeddingRequest(
                model.getModel(),
                inputs,
                "float",
                model.getDimensions(),
                null
        );

        String provider = providerFromUrl(model.getEndpointUrl());
        String url = resolveUrl(model.getEndpointUrl());
        log.info("调用嵌入模型: model={}, endpoint={}", model.getModel(), url);

        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        WebClient.RequestHeadersSpec<?> spec = webClient.post()
                .uri(url)
                .bodyValue(request);
        if (StringUtils.hasText(model.getSk())) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + model.getSk());
        }

        try {
            EmbeddingResponse response = spec.retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(body -> mapHttpException(clientResponse.statusCode().value(), body, model, provider, null)))
                    .bodyToMono(EmbeddingResponse.class)
                    .block();
            if (response == null || response.data == null) {
                throw new LlmProviderException("嵌入模型返回为空", null, LlmErrorCode.PROVIDER_ERROR,
                        provider, model.getModel(), false, 0, null, null, null);
            }
            return response.data.stream()
                    .sorted(Comparator.comparingInt(d -> d.index == null ? 0 : d.index))
                    .map(d -> d.embedding)
                    .toList();
        } catch (LlmException e) {
            throw e;
        } catch (Throwable t) {
            throw mapException(t, model, provider);
        }
    }

    private String providerFromUrl(String endpointUrl) {
        try {
            URI uri = URI.create(endpointUrl);
            return uri.getHost();
        } catch (Exception e) {
            return endpointUrl;
        }
    }

    private String resolveUrl(String endpointUrl) {
        return endpointUrl.endsWith("/") ? endpointUrl.substring(0, endpointUrl.length() - 1) : endpointUrl;
    }

    private LlmProviderException mapHttpException(int statusCode, String body, EmbeddingModel model, String provider, Throwable cause) {
        String providerMessage = null;
        String providerErrorCode = null;
        if (StringUtils.hasText(body)) {
            try {
                com.gacfox.proarc.agentic.model.openai.OpenAiErrorResponse resp =
                        new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, com.gacfox.proarc.agentic.model.openai.OpenAiErrorResponse.class);
                if (resp != null && resp.getError() != null) {
                    providerMessage = resp.getError().getMessage();
                    providerErrorCode = resp.getError().getCode();
                }
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.hasText(providerMessage)) {
            providerMessage = providerMessage.trim();
            if (providerMessage.length() > 500) {
                providerMessage = providerMessage.substring(0, 500) + "... [truncated]";
            }
        }
        String base = "Embedding provider error (HTTP " + statusCode + ")";
        String message = buildErrorMessage(base, providerMessage, providerErrorCode);
        if (statusCode == 401 || statusCode == 403) {
            return new LlmAuthException(message, cause, provider, model.getModel(), statusCode, body, providerErrorCode);
        }
        if (statusCode == 400) {
            return new LlmBadRequestException(message, cause, provider, model.getModel(), statusCode, body, providerErrorCode);
        }
        if (statusCode == 404) {
            return new LlmNotFoundException(message, cause, provider, model.getModel(), statusCode, body, providerErrorCode);
        }
        if (statusCode == 429) {
            Long retryAfter = null;
            if (cause instanceof WebClientResponseException e && e.getHeaders().getFirst("Retry-After") != null) {
                try {
                    String retryAfterStr = e.getHeaders().getFirst("Retry-After");
                    if (StringUtils.hasText(retryAfterStr)) {
                        retryAfter = Long.parseLong(retryAfterStr) * 1000L;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return new LlmRateLimitException(message, cause, provider, model.getModel(), statusCode, body, providerErrorCode, retryAfter);
        }
        if (statusCode >= 500) {
            return new LlmServerException(message, cause, provider, model.getModel(), statusCode, body, providerErrorCode);
        }
        return new LlmProviderException(message, cause, LlmErrorCode.PROVIDER_ERROR, provider, model.getModel(), false,
                statusCode, body, providerErrorCode, null);
    }

    private LlmException mapException(Throwable throwable, EmbeddingModel model, String provider) {
        if (throwable instanceof WebClientResponseException responseException) {
            return mapHttpException(responseException.getStatusCode().value(),
                    responseException.getResponseBodyAsString(), model, provider, responseException);
        }
        if (throwable instanceof TimeoutException
                || throwable instanceof io.netty.handler.timeout.ReadTimeoutException
                || throwable instanceof ClosedChannelException) {
            return new LlmTimeoutException("Embedding call timed out: " + throwable.getMessage(),
                    throwable, provider, model.getModel());
        }
        if (throwable instanceof org.springframework.web.reactive.function.client.WebClientRequestException wcre) {
            Throwable cause = wcre.getCause();
            if (cause instanceof UnknownHostException) {
                return new LlmNetworkException("DNS resolution failed: " + cause.getMessage(),
                        cause, provider, model.getModel());
            }
            if (cause instanceof ConnectException) {
                return new LlmNetworkException("Connection refused: " + cause.getMessage(),
                        cause, provider, model.getModel());
            }
            if (cause instanceof SSLException) {
                return new LlmNetworkException("SSL handshake failed: " + cause.getMessage(),
                        cause, provider, model.getModel());
            }
            return new LlmNetworkException("Network error: " + wcre.getMessage(),
                    wcre, provider, model.getModel());
        }
        if (throwable instanceof LlmException) {
            return (LlmException) throwable;
        }
        return new LlmNetworkException("Unexpected embedding error: " + throwable.getMessage(),
                throwable, provider, model.getModel());
    }

    private static String buildErrorMessage(String base, String providerMessage, String providerErrorCode) {
        if (!StringUtils.hasText(providerMessage)) {
            return base;
        }
        if (StringUtils.hasText(providerErrorCode)) {
            return base + ": " + providerErrorCode + " - " + providerMessage;
        }
        return base + ": " + providerMessage;
    }

    public record EmbeddingRequest(
            String model,
            Object input,
            @JsonProperty("encoding_format") String encodingFormat,
            Integer dimensions,
            String user
    ) {
    }

    public record EmbeddingResponse(
            String object,
            List<EmbeddingData> data,
            String model,
            @JsonProperty("usage") EmbeddingUsage usage
    ) {
    }

    public record EmbeddingData(
            String object,
            List<Double> embedding,
            Integer index
    ) {
    }

    public record EmbeddingUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
