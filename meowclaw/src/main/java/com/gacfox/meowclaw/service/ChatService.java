package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.dto.ChatEventDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.LlmRepository;
import com.gacfox.meowclaw.interceptor.agent.AgentLoggingInterceptor;
import com.gacfox.meowclaw.interceptor.agent.AgentSystemPromptRefreshInterceptor;
import com.gacfox.meowclaw.interceptor.llm.LlmLoggingInterceptor;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageAccumulator;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageLlmInterceptor;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.agent.ReActAgentExecutor;
import com.gacfox.proarc.agentic.client.LlmClient;
import com.gacfox.proarc.agentic.client.OpenAiLlmClient;
import com.gacfox.proarc.agentic.client.interceptor.builtin.RetryInterceptor;
import com.gacfox.proarc.agentic.model.ChatRequest;
import com.gacfox.proarc.agentic.model.openai.Message;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.agentic.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ChatService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConversationService conversationService;
    private final ChatPersistenceService chatPersistenceService;
    private final AgentRepository agentRepository;
    private final LlmRepository llmRepository;
    private final ToolRegistry toolRegistry;
    private final HttpClient httpClient;
    private final AgentLoggingInterceptor agentLoggingInterceptor;
    private final AgentSystemPromptRefreshInterceptor agentSystemPromptRefreshInterceptor;
    private final LlmLoggingInterceptor llmLoggingInterceptor;
    private final TitleGenerationRegistryService titleGenerationRegistryService;
    private final TokenUsageLogService tokenUsageLogService;
    private final ContextCompressionService contextCompressionService;

    @Autowired
    public ChatService(ConversationService conversationService,
                       ChatPersistenceService chatPersistenceService,
                       AgentRepository agentRepository,
                       LlmRepository llmRepository,
                       ToolRegistry toolRegistry,
                       HttpClient httpClient,
                       AgentLoggingInterceptor agentLoggingInterceptor,
                       AgentSystemPromptRefreshInterceptor agentSystemPromptRefreshInterceptor,
                       LlmLoggingInterceptor llmLoggingInterceptor,
                       TitleGenerationRegistryService titleGenerationRegistryService,
                       TokenUsageLogService tokenUsageLogService,
                       ContextCompressionService contextCompressionService) {
        this.conversationService = conversationService;
        this.chatPersistenceService = chatPersistenceService;
        this.agentRepository = agentRepository;
        this.llmRepository = llmRepository;
        this.toolRegistry = toolRegistry;
        this.httpClient = httpClient;
        this.agentLoggingInterceptor = agentLoggingInterceptor;
        this.agentSystemPromptRefreshInterceptor = agentSystemPromptRefreshInterceptor;
        this.llmLoggingInterceptor = llmLoggingInterceptor;
        this.titleGenerationRegistryService = titleGenerationRegistryService;
        this.tokenUsageLogService = tokenUsageLogService;
        this.contextCompressionService = contextCompressionService;
    }

    public Flux<ChatEventDTO> chat(Long conversationId, String userContent) {
        return Flux.<ChatEventDTO>create(sink -> {
            try {
                Conversation conv = conversationService.getById(conversationId);
                Agent agent = agentRepository.findById(conv.getAgentId())
                        .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
                Llm llm = llmRepository.findById(agent.getLlmId())
                        .orElseThrow(() -> new IllegalArgumentException("LLM配置不存在"));
                Llm secondaryLlm = llmRepository.findById(agent.getSecondaryLlmId())
                        .orElseThrow(() -> new IllegalArgumentException("辅助LLM配置不存在"));

                tryProactiveCompression(conversationId, secondaryLlm, sink);

                ChatEventBatch batch = chatPersistenceService.createBatch(conversationId, userContent);
                Long batchId = batch.getId();

                TokenUsageAccumulator tokenAccum = new TokenUsageAccumulator();
                LlmClient llmClient = buildMainLlmClient(llm, batchId, conv, tokenAccum);
                LlmClient secondaryLlmClient = buildAuxiliaryLlmClient(secondaryLlm, batchId, conv);

                List<String> toolNames = new ArrayList<>(parseJsonArray(agent.getEnabledTools()));
                toolNames.addAll(parseJsonArray(agent.getEnabledMcpTools()));
                ReActAgentExecutor executor = buildExecutor(llmClient, toolNames);

                AgentContext context = buildAgentContext(agent, conv, llm, userContent, toolNames, llmClient);
                boolean isFirstBatch = conv.getTitle() == null || conv.getTitle().isBlank();

                executeAgent(context, executor, batchId, conversationId, userContent, llm, secondaryLlm, secondaryLlmClient, tokenAccum, isFirstBatch, sink);
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void tryProactiveCompression(Long conversationId, Llm llm,
                                         reactor.core.publisher.FluxSink<ChatEventDTO> sink) {
        if (!"VERY_LOW".equals(conversationService.getContextStatus(conversationId))) {
            return;
        }
        try {
            if (contextCompressionService.proactivelyCompress(conversationId, llm,
                    conversationService.getContextPromptTokens(conversationId))) {
                sink.next(ChatEventDTO.builder().type("context_compression")
                        .content("系统已进行主动上下文压缩，以继续执行当前任务。").build());
            }
        } catch (Exception compressionError) {
            log.warn("Proactive context compression failed for conversation {}", conversationId, compressionError);
        }
    }

    private LlmClient buildMainLlmClient(Llm llm, Long batchId, Conversation conv,
                                         TokenUsageAccumulator tokenAccum) {
        TokenUsageContext tokenUsageContext = new TokenUsageContext(
                llm.getId(), conv.getAgentId(), conv.getId(), batchId, llm.getModel());
        return OpenAiLlmClient.builder()
                .modelInfo(buildModelInfo(llm))
                .httpClient(httpClient)
                .interceptors(List.of(llmLoggingInterceptor,
                        new TokenUsageLlmInterceptor(tokenAccum, tokenUsageLogService, tokenUsageContext),
                        new RetryInterceptor()))
                .build();
    }

    private LlmClient buildAuxiliaryLlmClient(Llm llm, Long batchId, Conversation conv) {
        TokenUsageContext tokenUsageContext = new TokenUsageContext(
                llm.getId(), conv.getAgentId(), conv.getId(), batchId, llm.getModel());
        return OpenAiLlmClient.builder()
                .modelInfo(buildModelInfo(llm))
                .httpClient(httpClient)
                .interceptors(List.of(llmLoggingInterceptor,
                        new TokenUsageLlmInterceptor(new TokenUsageAccumulator(), tokenUsageLogService, tokenUsageContext),
                        new RetryInterceptor()))
                .build();
    }

    private ReActAgentExecutor buildExecutor(LlmClient llmClient, List<String> toolNames) {
        return ReActAgentExecutor.builder()
                .defaultLlmClient(llmClient)
                .toolRegistry(toolRegistry)
                .defaultToolNames(toolNames)
                .interceptors(List.of(agentSystemPromptRefreshInterceptor, agentLoggingInterceptor))
                .build();
    }

    private AgentContext buildAgentContext(Agent agent, Conversation conv, Llm llm, String userContent,
                                           List<String> toolNames, LlmClient llmClient) {
        String workspaceFolder = agent.getWorkspaceFolder();
        Map<String, Object> variables = new HashMap<>();
        if (workspaceFolder != null && !workspaceFolder.isBlank()) {
            variables.put("workspacePath", workspaceFolder);
        }
        String currentCwd = workspaceFolder;
        String contextJson = conv.getContextJson();
        if (contextJson != null && !contextJson.isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = OBJECT_MAPPER.readTree(contextJson);
                com.fasterxml.jackson.databind.JsonNode cwdNode = node.get("cwd");
                if (cwdNode != null && cwdNode.isTextual()) {
                    currentCwd = cwdNode.asText();
                }
            } catch (Exception ignored) {
            }
        }
        variables.put("cwd", currentCwd);
        variables.put("agentId", agent.getId());
        variables.put("conversationId", conv.getId());

        Double temperature = llm.getTemperature() != null ? llm.getTemperature() / 100.0 : 0.7;

        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
                .role(Message.ROLE_SYSTEM).content("").build());
        messages.addAll(contextCompressionService.buildMessages(conv.getId()));
        messages.add(Message.builder()
                .role(Message.ROLE_USER).content(userContent).build());

        return AgentContext.builder()
                .messages(messages)
                .llmClient(llmClient)
                .toolNames(toolNames)
                .temperature(temperature)
                .maxTokens(llm.getMaxTokens())
                .variables(variables)
                .build();
    }

    private void executeAgent(AgentContext context, ReActAgentExecutor executor, Long batchId, Long conversationId,
                              String userContent,
                              Llm llm, Llm secondaryLlm, LlmClient secondaryLlmClient,
                              TokenUsageAccumulator tokenAccum, boolean isFirstBatch,
                              FluxSink<ChatEventDTO> sink) {
        int messageCountBefore = context.getMessages().size();
        AtomicInteger eventOrder = new AtomicInteger(0);
        AtomicReference<String> firstFinalAnswer = new AtomicReference<>();

        executor.execute(context)
                .map(response -> switch (response.getType()) {
                    case THINKING -> ChatEventDTO.builder()
                            .type("thinking")
                            .content(response.getContent())
                            .build();
                    case TOOL_CALL -> ChatEventDTO.builder()
                            .type("tool_call")
                            .toolCallId(response.getToolCallId())
                            .toolName(response.getToolName())
                            .toolArguments(response.getToolArguments())
                            .build();
                    case TOOL_RESULT -> ChatEventDTO.builder()
                            .type("tool_result")
                            .toolCallId(response.getToolCallId())
                            .toolName(response.getToolName())
                            .content(response.getContent())
                            .build();
                    case FINAL_ANSWER -> ChatEventDTO.builder()
                            .type("final_answer")
                            .content(response.getContent())
                            .build();
                    case ERROR -> ChatEventDTO.builder()
                            .type("error")
                            .content(response.getContent())
                            .build();
                })
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(event -> {
                    chatPersistenceService.saveChatEvent(
                            batchId, eventOrder.getAndIncrement(), event.getType(),
                            event.getContent(), event.getToolName(), event.getToolCallId(), event.getToolArguments());
                    if ("final_answer".equals(event.getType()) && isFirstBatch
                            && firstFinalAnswer.get() == null && event.getContent() != null) {
                        firstFinalAnswer.set(event.getContent());
                    }
                })
                .doOnComplete(() -> {
                    List<Message> newMessages =
                            context.getMessages().subList(messageCountBefore, context.getMessages().size());
                    chatPersistenceService.completeBatch(batchId, conversationId, newMessages,
                            tokenAccum.getInputTokens(), tokenAccum.getOutputTokens());
                    long promptTokens = tokenAccum.getLastPromptTokens();
                    String contextStatus;
                    Integer contextLength = llm.getContextLength();
                    Integer maxTokens = llm.getMaxTokens();
                    if (maxTokens == null || maxTokens <= 0 || maxTokens > 8192) {
                        maxTokens = 8192;
                    }
                    if (contextLength == null || contextLength <= 0 || promptTokens <= 0) {
                        contextStatus = "NORMAL";
                    } else {
                        int available = contextLength - maxTokens;
                        if (available <= 0) available = contextLength;
                        double ratio = (double) promptTokens / available;
                        if (ratio >= 0.93) {
                            contextStatus = "VERY_LOW";
                        } else if (ratio >= 0.82) {
                            contextStatus = "LOW";
                        } else {
                            contextStatus = "NORMAL";
                        }
                    }
                    conversationService.updateContextHealth(conversationId, promptTokens, contextLength, contextStatus);
                    sink.next(ChatEventDTO.builder().type("context_status").content(contextStatus).build());
                    try {
                        contextCompressionService.afterBatch(conversationId, batchId, secondaryLlm);
                    } catch (Exception recapError) {
                        log.warn("Rolling context recap failed for conversation {}", conversationId, recapError);
                    }
                    Object cwd = context.getVariables().get("cwd");
                    if (cwd instanceof String cwdStr) {
                        try {
                            conversationService.updateCwd(conversationId, cwdStr);
                        } catch (Exception ignored) {
                        }
                    }
                    if (isFirstBatch && firstFinalAnswer.get() != null) {
                        CompletableFuture<String> ignored = titleGenerationRegistryService.register(conversationId);
                        Mono.fromRunnable(() -> generateTitle(conversationId, userContent, firstFinalAnswer.get(), secondaryLlmClient))
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe();
                    }
                })
                .doOnError(e -> {
                    log.error("Chat execution error for conversation {}", conversationId, e);
                    chatPersistenceService.failBatch(batchId, e.getMessage());
                })
                .subscribe(sink::next, sink::error, sink::complete);
    }

    private ModelInfo buildModelInfo(Llm llm) {
        return ModelInfo.builder()
                .provider("openai")
                .model(llm.getModel())
                .endpoint(llm.getEndpointUrl())
                .sk(llm.getSk())
                .maxTokens(llm.getMaxTokens())
                .contextLength(llm.getContextLength())
                .build();
    }

    private void generateTitle(Long conversationId, String userContent, String finalAnswer, LlmClient llmClient) {
        String title;
        try {
            String prompt = "根据以下用户问题和AI回答，生成一个简短的对话标题（不超过20个字，不要加引号，直接输出标题文本）：\n\n"
                    + "用户：" + userContent + "\n\nAI：" + finalAnswer;
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            Message.builder()
                                    .role(Message.ROLE_USER)
                                    .content(prompt)
                                    .build()))
                    .temperature(0.7)
                    .build();
            ModelResponse response = llmClient.blockingChat(request);
            title = response.extractBlockingContent();
            if (title == null || title.isBlank()) {
                title = userContent.length() > 50 ? userContent.substring(0, 50) + "..." : userContent;
            }
        } catch (Exception e) {
            log.warn("Failed to generate title for conversation {}", conversationId, e);
            title = userContent.length() > 50 ? userContent.substring(0, 50) + "..." : userContent;
        }
        if (title.length() > 100) {
            title = title.substring(0, 100);
        }
        String finalTitle = title.trim();
        conversationService.updateTitle(conversationId, finalTitle);
        titleGenerationRegistryService.complete(conversationId, finalTitle);
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
