package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gacfox.meowclaw.entity.EmbeddingModel;
import com.gacfox.meowclaw.repository.EmbeddingModelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/**
 * 向量嵌入模型调用服务
 */
@Slf4j
@Service
public class EmbeddingService {
    private final EmbeddingModelRepository embeddingModelRepository;
    private final RestClient restClient;

    @Autowired
    public EmbeddingService(EmbeddingModelRepository embeddingModelRepository, RestClient restClient) {
        this.embeddingModelRepository = embeddingModelRepository;
        this.restClient = restClient;
    }

    /**
     * 调用向量嵌入模型获取向量列表
     *
     * @param embeddingModelId 模型配置ID
     * @param inputs           待嵌入文本列表
     * @return 与输入顺序对应的向量列表
     */
    public List<float[]> embed(Long embeddingModelId, List<String> inputs) {
        EmbeddingModel model = embeddingModelRepository.findById(embeddingModelId)
                .orElseThrow(() -> new IllegalArgumentException("Embedding model configuration not found"));
        return embed(model, inputs);
    }

    /**
     * 调用向量嵌入模型获取向量列表
     *
     * @param model  模型配置
     * @param inputs 待嵌入文本列表
     * @return 与输入顺序对应的向量列表
     */
    public List<float[]> embed(EmbeddingModel model, List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }

        EmbeddingRequest request = new EmbeddingRequest(
                model.getModel(),
                inputs,
                "float",
                model.getDimensions()
        );

        String endpointUrl = model.getEndpointUrl();
        String url = endpointUrl.endsWith("/") ? endpointUrl.substring(0, endpointUrl.length() - 1) : endpointUrl;
        log.info("Embedding with [{}] by {}", model.getModel(), url);

        try {
            EmbeddingResponse response = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .headers(headers -> {
                        if (StringUtils.hasText(model.getSk())) {
                            headers.setBearerAuth(model.getSk());
                        }
                    })
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String body = "";
                        try (InputStream is = resp.getBody()) {
                            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        } catch (IOException ignored) {
                        }
                        log.error("Embedding error: HTTP {} endpoint={}, model={}, body={}",
                                resp.getStatusCode().value(), url, model.getModel(), body);
                        throw new IllegalStateException("Embedding error: HTTP " + resp.getStatusCode().value());
                    })
                    .body(EmbeddingResponse.class);
            if (response == null || response.data == null) {
                log.error("Embedding error: result is empty! endpoint={}, model={}",
                        url, model.getModel());
                throw new IllegalStateException("Embedding error: result is empty!");
            }
            log.info("Embedding completed with [{}]", model.getModel());
            return response.data.stream()
                    .sorted(Comparator.comparingInt(d -> d.index == null ? 0 : d.index))
                    .map(d -> d.embedding)
                    .toList();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding error: endpoint={}, model={}", url, model.getModel(), e);
            throw new RuntimeException("Embedding error: " + e.getMessage(), e);
        }
    }

    public record EmbeddingRequest(
            String model,
            Object input,
            @JsonProperty("encoding_format") String encodingFormat,
            Integer dimensions
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
            float[] embedding,
            Integer index
    ) {
    }

    public record EmbeddingUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
