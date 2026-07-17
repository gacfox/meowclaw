package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.dto.ChatEventDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.entity.Message;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.LlmRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import com.gacfox.meowclaw.interceptor.agent.AgentLoggingInterceptor;
import com.gacfox.meowclaw.interceptor.agent.AgentSystemPromptRefreshInterceptor;
import com.gacfox.meowclaw.interceptor.llm.LlmLoggingInterceptor;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageAccumulator;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageLlmInterceptor;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.agent.AgentResponse;
import com.gacfox.proarc.agentic.agent.ReActAgentExecutor;
import com.gacfox.proarc.agentic.client.LlmClient;
import com.gacfox.proarc.agentic.client.OpenAiLlmClient;
import com.gacfox.proarc.agentic.model.ChatRequest;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.agentic.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final MessageRepository messageRepository;
    private final ToolRegistry toolRegistry;
    private final HttpClient httpClient;
    private final AgentLoggingInterceptor agentLoggingInterceptor;
    private final AgentSystemPromptRefreshInterceptor agentSystemPromptRefreshInterceptor;
    private final LlmLoggingInterceptor llmLoggingInterceptor;
    private final TitleGenerationRegistry titleGenerationRegistry;
    private final TokenUsageLogService tokenUsageLogService;

    @Autowired
    public ChatService(ConversationService conversationService,
                       ChatPersistenceService chatPersistenceService,
                       AgentRepository agentRepository,
                       LlmRepository llmRepository,
                       MessageRepository messageRepository,
                       ToolRegistry toolRegistry,
                       HttpClient httpClient,
                       AgentLoggingInterceptor agentLoggingInterceptor,
                       AgentSystemPromptRefreshInterceptor agentSystemPromptRefreshInterceptor,
                       LlmLoggingInterceptor llmLoggingInterceptor,
                       TitleGenerationRegistry titleGenerationRegistry,
                       TokenUsageLogService tokenUsageLogService) {
        this.conversationService = conversationService;
        this.chatPersistenceService = chatPersistenceService;
        this.agentRepository = agentRepository;
        this.llmRepository = llmRepository;
        this.messageRepository = messageRepository;
        this.toolRegistry = toolRegistry;
        this.httpClient = httpClient;
        this.agentLoggingInterceptor = agentLoggingInterceptor;
        this.agentSystemPromptRefreshInterceptor = agentSystemPromptRefreshInterceptor;
        this.llmLoggingInterceptor = llmLoggingInterceptor;
        this.titleGenerationRegistry = titleGenerationRegistry;
        this.tokenUsageLogService = tokenUsageLogService;
    }

    public Flux<ChatEventDTO> chat(Long conversationId, String userContent) {
        return Flux.<ChatEventDTO>create(sink -> {
            try {
                Conversation conv = conversationService.getById(conversationId);
                Agent agent = agentRepository.findById(conv.getAgentId())
                        .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
                Llm llm = llmRepository.findById(agent.getLlmId())
                        .orElseThrow(() -> new IllegalArgumentException("LLM配置不存在"));

                ChatEventBatch batch = chatPersistenceService.createBatch(conversationId, userContent);
                Long batchId = batch.getId();

                ModelInfo modelInfo = ModelInfo.builder()
                        .provider("openai")
                        .model(llm.getModel())
                        .endpoint(llm.getEndpointUrl())
                        .sk(llm.getSk())
                        .maxTokens(llm.getMaxTokens())
                        .contextLength(llm.getContextLength())
                        .build();
                TokenUsageAccumulator tokenAccum = new TokenUsageAccumulator();
                TokenUsageContext tokenUsageContext = new TokenUsageContext(
                        agent.getLlmId(), conv.getAgentId(), conversationId, batchId, llm.getModel());
                LlmClient llmClient = OpenAiLlmClient.builder()
                        .modelInfo(modelInfo)
                        .httpClient(httpClient)
                        .interceptors(List.of(llmLoggingInterceptor,
                                new TokenUsageLlmInterceptor(tokenAccum, tokenUsageLogService, tokenUsageContext)))
                        .build();

                String workspaceFolder = agent.getWorkspaceFolder();
                Map<String, Object> variables = new HashMap<>();
                if (workspaceFolder != null && !workspaceFolder.isBlank()) {
                    variables.put("workspacePath", workspaceFolder);
                }
                String savedCwd = extractCwd(conv.getContextJson());
                String currentCwd = savedCwd != null ? savedCwd : workspaceFolder;
                variables.put("cwd", currentCwd);
                variables.put("agentId", agent.getId());

                List<com.gacfox.proarc.agentic.model.openai.Message> messages =
                        buildContextMessages(conversationId, userContent);

                List<String> toolNames = new ArrayList<>(parseJsonArray(agent.getEnabledTools()));
                toolNames.addAll(parseJsonArray(agent.getEnabledMcpTools()));
                Double temperature = llm.getTemperature() != null ? llm.getTemperature() / 100.0 : 0.7;

                ReActAgentExecutor executor = ReActAgentExecutor.builder()
                        .defaultLlmClient(llmClient)
                        .toolRegistry(toolRegistry)
                        .defaultToolNames(toolNames)
                        .interceptors(List.of(agentSystemPromptRefreshInterceptor, agentLoggingInterceptor))
                        .build();

                AgentContext context = AgentContext.builder()
                        .messages(messages)
                        .llmClient(llmClient)
                        .toolNames(toolNames)
                        .temperature(temperature)
                        .maxTokens(llm.getMaxTokens())
                        .variables(variables)
                        .build();

                int messageCountBefore = context.getMessages().size();
                AtomicInteger eventOrder = new AtomicInteger(0);
                AtomicReference<String> firstFinalAnswer = new AtomicReference<>();
                boolean isFirstBatch = conv.getTitle() == null || conv.getTitle().isBlank();

                executor.execute(context)
                        .map(this::toChatEvent)
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
                            List<com.gacfox.proarc.agentic.model.openai.Message> newMessages =
                                    context.getMessages().subList(messageCountBefore, context.getMessages().size());
                            chatPersistenceService.completeBatch(batchId, conversationId, newMessages,
                                    tokenAccum.getInputTokens(), tokenAccum.getOutputTokens());
                            Object cwd = context.getVariables().get("cwd");
                            if (cwd instanceof String cwdStr) {
                                try {
                                    conversationService.updateContextJson(conversationId,
                                            OBJECT_MAPPER.writeValueAsString(Map.of("cwd", cwdStr)));
                                } catch (Exception ignored) {
                                }
                            }
                            if (isFirstBatch && firstFinalAnswer.get() != null) {
                                titleGenerationRegistry.register(conversationId);
                                Mono.fromRunnable(() -> generateTitle(conversationId, userContent, firstFinalAnswer.get(), llmClient))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .subscribe();
                            }
                        })
                        .doOnError(e -> {
                            log.error("Chat execution error for conversation {}", conversationId, e);
                            chatPersistenceService.failBatch(batchId, e.getMessage());
                        })
                        .subscribe(sink::next, sink::error, sink::complete);
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void generateTitle(Long conversationId, String userContent, String finalAnswer, LlmClient llmClient) {
        String title;
        try {
            String prompt = "根据以下用户问题和AI回答，生成一个简短的对话标题（不超过20个字，不要加引号，直接输出标题文本）：\n\n"
                    + "用户：" + userContent + "\n\nAI：" + finalAnswer;
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            com.gacfox.proarc.agentic.model.openai.Message.builder()
                                    .role(com.gacfox.proarc.agentic.model.openai.Message.ROLE_USER)
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
        titleGenerationRegistry.complete(conversationId, finalTitle);
    }

    private List<com.gacfox.proarc.agentic.model.openai.Message> buildContextMessages(
            Long conversationId, String userContent) {
        List<com.gacfox.proarc.agentic.model.openai.Message> messages = new ArrayList<>();

        List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        for (Message msg : history) {
            messages.add(chatPersistenceService.toProarcMessage(msg));
        }

        messages.add(com.gacfox.proarc.agentic.model.openai.Message.builder()
                .role(com.gacfox.proarc.agentic.model.openai.Message.ROLE_USER)
                .content(userContent)
                .build());

        return messages;
    }

    private ChatEventDTO toChatEvent(AgentResponse response) {
        return switch (response.getType()) {
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
        };
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String extractCwd(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) return null;
        try {
            JsonNode node = OBJECT_MAPPER.readTree(contextJson);
            JsonNode cwdNode = node.get("cwd");
            return cwdNode != null && cwdNode.isTextual() ? cwdNode.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
