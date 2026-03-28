package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.LlmConfigDto;
import com.gacfox.meowclaw.entity.LlmConfig;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.LlmConfigRepository;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LlmConfigService {
    private final LlmConfigRepository llmConfigRepository;

    public LlmConfigService(LlmConfigRepository llmConfigRepository) {
        this.llmConfigRepository = llmConfigRepository;
    }

    public List<LlmConfigDto> findAll() {
        return llmConfigRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public LlmConfigDto findById(Long id) {
        LlmConfig config = llmConfigRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("LLM配置不存在"));
        return toDto(config);
    }

    public LlmConfigDto create(LlmConfigDto dto) {
        LlmConfig config = new LlmConfig();
        BeanUtils.copyProperties(dto, config);

        Instant now = Instant.now();
        config.setCreatedAtInstant(now);
        config.setUpdatedAtInstant(now);

        llmConfigRepository.save(config);
        return toDto(config);
    }

    public LlmConfigDto update(Long id, LlmConfigDto dto) {
        LlmConfig config = llmConfigRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("LLM配置不存在"));

        BeanUtils.copyProperties(dto, config, "id", "createdAt");
        config.setUpdatedAtInstant(Instant.now());

        llmConfigRepository.save(config);
        return toDto(config);
    }

    public void delete(Long id) {
        llmConfigRepository.deleteById(id);
    }

    public boolean testConnection(LlmConfigDto dto) {
        try {
            OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
                    .baseUrl(dto.getApiUrl())
                    .timeout(Duration.ofSeconds(30));
            if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
                clientBuilder.apiKey(dto.getApiKey());
            }
            OpenAIClient client = clientBuilder.build();

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(dto.getModel())
                    .messages(List.of(ChatCompletionMessageParam.ofUser(
                            ChatCompletionUserMessageParam.builder().content("Hello").build()
                    )))
                    .maxTokens(10)
                    .build();

            ChatCompletion completion = client.chat().completions().create(params);
            completion.choices();
            return !completion.choices().isEmpty();
        } catch (Exception e) {
            log.error("LLM连接测试失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private LlmConfigDto toDto(LlmConfig config) {
        LlmConfigDto dto = new LlmConfigDto();
        BeanUtils.copyProperties(config, dto);
        return dto;
    }
}
