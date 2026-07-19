package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.MemoryExtractionResult;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.entity.MemoryEntity;
import com.gacfox.meowclaw.interceptor.llm.LlmLoggingInterceptor;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageAccumulator;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageLlmInterceptor;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.LlmRepository;
import com.gacfox.meowclaw.repository.MemoryEntityRepository;
import com.gacfox.proarc.agentic.client.LlmClient;
import com.gacfox.proarc.agentic.client.OpenAiLlmClient;
import com.gacfox.proarc.agentic.client.interceptor.builtin.RetryInterceptor;
import com.gacfox.proarc.agentic.model.openai.Message;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.prompt.PromptTemplate;
import com.gacfox.proarc.agentic.structured.StructuredChatRequest;
import com.gacfox.proarc.agentic.structured.StructuredExecutor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 调用辅助LLM抽取记忆结构
 */
@Slf4j
@Service
public class MemoryExtractionService {
    private static final int MAX_REFERENCE_ENTITIES = 200;

    private final AgentRepository agentRepository;
    private final LlmRepository llmRepository;
    private final MemoryEntityRepository memoryEntityRepository;
    private final MemoryLuceneSearcher luceneSearcher;
    private final EmbeddingService embeddingService;
    private final HttpClient httpClient;
    private final LlmLoggingInterceptor llmLoggingInterceptor;
    private final TokenUsageLogService tokenUsageLogService;

    @Value("classpath:prompt/memory-extraction-prompt.md")
    private Resource promptResource;

    private String promptTemplate;

    @Autowired
    public MemoryExtractionService(AgentRepository agentRepository,
                                   LlmRepository llmRepository,
                                   MemoryEntityRepository memoryEntityRepository,
                                   MemoryLuceneSearcher luceneSearcher,
                                   EmbeddingService embeddingService,
                                   HttpClient httpClient,
                                   LlmLoggingInterceptor llmLoggingInterceptor,
                                   TokenUsageLogService tokenUsageLogService) {
        this.agentRepository = agentRepository;
        this.llmRepository = llmRepository;
        this.memoryEntityRepository = memoryEntityRepository;
        this.luceneSearcher = luceneSearcher;
        this.embeddingService = embeddingService;
        this.httpClient = httpClient;
        this.llmLoggingInterceptor = llmLoggingInterceptor;
        this.tokenUsageLogService = tokenUsageLogService;
    }

    @PostConstruct
    public void init() throws IOException {
        this.promptTemplate = promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    public MemoryExtractionResult extract(Long agentId, String type, String content, Long conversationId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        Llm secondaryLlm = llmRepository.findById(agent.getSecondaryLlmId())
                .orElseThrow(() -> new IllegalArgumentException("辅助LLM配置不存在"));

        String referenceEntities = buildReferenceEntities(agentId, content);
        String userPrompt = PromptTemplate.build(promptTemplate, Map.of(
                "type", type,
                "content", content,
                "existingEntities", referenceEntities
        ));

        LlmClient client = buildLlmClient(secondaryLlm, agentId, conversationId);
        StructuredExecutor executor = new StructuredExecutor(client);
        StructuredChatRequest<MemoryExtractionResult> request = StructuredChatRequest.<MemoryExtractionResult>builder()
                .messages(List.of(Message.builder()
                        .role(Message.ROLE_USER)
                        .content(userPrompt)
                        .build()))
                .responseType(MemoryExtractionResult.class)
                .toolName("extract_memory")
                .temperature(0.1)
                .maxTokens(1200)
                .build();

        MemoryExtractionResult result = executor.execute(request).extract();
        if (result.getType() == null) {
            result.setType(type);
        }
        if (result.getContent() == null || result.getContent().isBlank()) {
            result.setContent(content);
        }
        return result;
    }

    private String buildReferenceEntities(Long agentId, String content) {
        long count = memoryEntityRepository.countByAgentId(agentId);
        List<MemoryEntity> entities;
        if (count < MAX_REFERENCE_ENTITIES) {
            // 实体数小于MAX_REFERENCE_ENTITIES，直接全量加载
            entities = memoryEntityRepository.findByAgentId(agentId);
        } else {
            // 实体数不小于MAX_REFERENCE_ENTITIES，检索最相关的200个实体
            Agent agent = agentRepository.findById(agentId).orElse(null);
            float[] vector = null;
            if (agent != null && agent.getEmbeddingModelId() != null) {
                List<float[]> results = embeddingService.embed(agent.getEmbeddingModelId(), List.of(content));
                if (!results.isEmpty()) {
                    vector = results.get(0);
                }
            }
            List<Long> entityIds = luceneSearcher.searchRelationEntityIds(
                    agentId, content, vector, MAX_REFERENCE_ENTITIES * 2, MAX_REFERENCE_ENTITIES);
            entities = memoryEntityRepository.findAllById(entityIds);
        }
        if (entities.isEmpty()) {
            return "";
        }
        return entities.stream()
                .map(MemoryEntity::getName)
                .distinct()
                .map(name -> "- " + name)
                .collect(Collectors.joining("\n"));
    }

    private LlmClient buildLlmClient(Llm llm, Long agentId, Long conversationId) {
        ModelInfo modelInfo = ModelInfo.builder()
                .provider("openai")
                .model(llm.getModel())
                .endpoint(llm.getEndpointUrl())
                .sk(llm.getSk())
                .maxTokens(llm.getMaxTokens())
                .contextLength(llm.getContextLength())
                .build();
        TokenUsageContext tokenUsageContext = new TokenUsageContext(
                llm.getId(), agentId, conversationId, null, llm.getModel());
        return OpenAiLlmClient.builder()
                .modelInfo(modelInfo)
                .httpClient(httpClient)
                .interceptors(List.of(
                        llmLoggingInterceptor,
                        new TokenUsageLlmInterceptor(new TokenUsageAccumulator(), tokenUsageLogService, tokenUsageContext),
                        new RetryInterceptor()))
                .build();
    }
}
