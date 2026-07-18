package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.entity.ContextRecap;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.entity.Message;
import com.gacfox.meowclaw.interceptor.llm.LlmLoggingInterceptor;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageAccumulator;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageLlmInterceptor;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ContextRecapRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import com.gacfox.proarc.agentic.client.LlmClient;
import com.gacfox.proarc.agentic.client.OpenAiLlmClient;
import com.gacfox.proarc.agentic.client.interceptor.builtin.RetryInterceptor;
import com.gacfox.proarc.agentic.model.ChatRequest;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import com.gacfox.proarc.agentic.model.openai.ToolCall;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class ContextCompressionService {
    private static final String TRUNCATED = "... [此处因上下文压缩已截断]";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ChatEventBatchRepository batchRepository;
    private final ContextRecapRepository recapRepository;
    private final MessageRepository messageRepository;
    private final ChatPersistenceService persistenceService;
    private final TokenUsageLogService tokenUsageLogService;
    private final LlmLoggingInterceptor llmLoggingInterceptor;
    private final reactor.netty.http.client.HttpClient httpClient;

    @Value("classpath:prompt/context-recap-prompt.md")
    private Resource recapPromptResource;

    private String recapSystemPrompt;

    public ContextCompressionService(ChatEventBatchRepository batchRepository, ContextRecapRepository recapRepository,
                                     MessageRepository messageRepository, ChatPersistenceService persistenceService,
                                     TokenUsageLogService tokenUsageLogService, LlmLoggingInterceptor llmLoggingInterceptor,
                                     reactor.netty.http.client.HttpClient httpClient) {
        this.batchRepository = batchRepository;
        this.recapRepository = recapRepository;
        this.messageRepository = messageRepository;
        this.persistenceService = persistenceService;
        this.tokenUsageLogService = tokenUsageLogService;
        this.llmLoggingInterceptor = llmLoggingInterceptor;
        this.httpClient = httpClient;
    }

    @PostConstruct
    void loadRecapPrompt() throws IOException {
        try (InputStream input = recapPromptResource.getInputStream()) {
            recapSystemPrompt = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public List<com.gacfox.proarc.agentic.model.openai.Message> buildMessages(Long conversationId) {
        List<ChatEventBatch> batches = userBatches(conversationId);
        List<ContextRecap> recaps = recapRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        Set<Long> skipped = proactivelyCompressedBatchIds(recaps, batches);
        Map<Long, List<Message>> messagesByBatch = messagesByBatch(conversationId);
        List<com.gacfox.proarc.agentic.model.openai.Message> result = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            ChatEventBatch batch = batches.get(i);
            if (skipped.contains(batch.getId())) continue;
            int age = batches.size() - i;
            result.add(com.gacfox.proarc.agentic.model.openai.Message.builder()
                    .role(com.gacfox.proarc.agentic.model.openai.Message.ROLE_USER).content(batch.getUserContent()).build());
            for (Message message : messagesByBatch.getOrDefault(batch.getId(), List.of())) {
                if (age >= 15 && shouldFoldToolMessage(message)) continue;
                boolean truncate = age >= 5;
                var builder = com.gacfox.proarc.agentic.model.openai.Message.builder().role(message.getRole())
                        .content(truncate ? truncateContent(message.getContent()) : message.getContent())
                        .reasoningContent(message.getReasoningContent())
                        .toolCallId(message.getToolCallId());
                if (message.getToolCallsJson() != null) {
                    try {
                        List<ToolCall> calls = OBJECT_MAPPER.readValue(message.getToolCallsJson(),
                                new TypeReference<>() {
                                });
                        builder.toolCalls(calls);
                    } catch (Exception ignored) {
                    }
                }
                result.add(builder.build());
            }
        }
        return result;
    }

    public String buildRecapText(Long conversationId) {
        List<ContextRecap> recaps = recapRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (recaps.isEmpty()) return null;
        log.info("Loaded recaps for conversation {}: {}", conversationId,
                recaps.stream().map(ContextRecap::getId).toList());
        return recaps.stream()
                .map(ContextRecap::getContent)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse(null);
    }

    public void afterBatch(Long conversationId, Long completedBatchId, Llm llm) {
        LlmClient llmClient = buildCompressionLlmClient(llm, conversationId, completedBatchId);
        List<ChatEventBatch> batches = userBatches(conversationId);
        int current = -1;
        for (int i = 0; i < batches.size(); i++) {
            if (batches.get(i).getId().equals(completedBatchId)) {
                current = i;
                break;
            }
        }
        Map<Long, List<Message>> messagesByBatch = messagesByBatch(conversationId);
        if (current >= 5) {
            ChatEventBatch target = batches.get(current - 5);
            if (!recapRepository.existsByConversationIdAndFromBatchIdAndToBatchIdAndType(
                    conversationId, target.getId(), target.getId(), "ROLLING")) {
                String recapContent = summarize(batchText(target, messagesByBatch), llmClient);
                saveRecap(conversationId, target.getId(), target.getId(), "ROLLING", recapContent);
                log.info("Saved [ROLLING] Recap: {}", recapContent);
            }
        }

        List<ContextRecap> recaps = recapRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (recaps.size() >= 20) {
            List<ContextRecap> firstSix = recaps.subList(0, 6);
            String source = firstSix.stream().map(ContextRecap::getContent).reduce("", (a, b) -> a + "\n\n" + b);
            String recapContent = summarize(source, llmClient);
            recapRepository.deleteAll(firstSix);
            saveRecap(conversationId, firstSix.get(0).getFromBatchId(),
                    firstSix.get(firstSix.size() - 1).getToBatchId(), "ROLLED_UP", recapContent);
            log.info("Saved [ROLLED_UP] Recap: {}", recapContent);
        }
    }

    public boolean proactivelyCompress(Long conversationId, Llm llm, long promptTokens) {
        List<ChatEventBatch> batches = userBatches(conversationId);
        List<ContextRecap> recaps = recapRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        Set<Long> skipped = proactivelyCompressedBatchIds(recaps, batches);
        List<ChatEventBatch> candidates = batches.stream().filter(b -> !skipped.contains(b.getId())).toList();
        if (candidates.size() < 2) return false;
        Integer contextLength = llm.getContextLength();
        Integer maxTokens = llm.getMaxTokens();
        long budget = contextLength == null ? 0 : contextLength - (maxTokens == null ? 0 : maxTokens);
        long target = budget > 0 ? Math.round(budget * 0.75) : Math.round(promptTokens * 0.75);
        long requiredRelease = Math.max(1, promptTokens - target);
        List<ChatEventBatch> selected = new ArrayList<>();
        long released = 0;
        Map<Long, List<Message>> messagesByBatch = messagesByBatch(conversationId);
        for (ChatEventBatch candidate : candidates) {
            selected.add(candidate);
            String text = batchText(candidate, messagesByBatch);
            released += Math.max(1, (text.length() + 3L) / 4L);
            if (released >= requiredRelease) break;
        }
        StringBuilder source = new StringBuilder();
        for (ChatEventBatch batch : selected) source.append(batchText(batch, messagesByBatch)).append("\n\n");
        String recapContent = summarize(source.toString(), buildCompressionLlmClient(llm, conversationId, null));
        ChatEventBatch first = selected.get(0);
        ChatEventBatch last = selected.get(selected.size() - 1);
        saveRecap(conversationId, first.getId(), last.getId(), "PROACTIVE", recapContent);
        log.info("Saved [PROACTIVE] Recap: {}", recapContent);
        String compressionContent = "已主动压缩 " + selected.size() + " 个历史 batch（" + first.getId() + "-" + last.getId() + "）。";
        persistenceService.createContextCompressionBatch(conversationId, compressionContent);
        log.info("Saved compression batch: {}", compressionContent);
        return true;
    }

    private LlmClient buildCompressionLlmClient(Llm llm, Long conversationId, Long batchId) {
        TokenUsageContext tokenUsageContext = new TokenUsageContext(
                llm.getId(), null, conversationId, batchId, llm.getModel());
        TokenUsageAccumulator tokenAccum = new TokenUsageAccumulator();
        return OpenAiLlmClient.builder()
                .modelInfo(ModelInfo.builder()
                        .provider("openai")
                        .model(llm.getModel())
                        .endpoint(llm.getEndpointUrl())
                        .sk(llm.getSk())
                        .maxTokens(llm.getMaxTokens())
                        .contextLength(llm.getContextLength())
                        .build())
                .httpClient(httpClient)
                .interceptors(List.of(llmLoggingInterceptor,
                        new TokenUsageLlmInterceptor(tokenAccum, tokenUsageLogService, tokenUsageContext),
                        new RetryInterceptor()))
                .build();
    }

    private void saveRecap(Long conversationId, Long fromBatchId, Long toBatchId, String type, String content) {
        ContextRecap recap = new ContextRecap();
        recap.setConversationId(conversationId);
        recap.setFromBatchId(fromBatchId);
        recap.setToBatchId(toBatchId);
        recap.setType(type);
        recap.setContent(content);
        recap.setCreatedAt(System.currentTimeMillis());
        recapRepository.save(recap);
    }

    private String summarize(String source, LlmClient llmClient) {
        String bounded = source.length() > 24000 ? source.substring(0, 24000) : source;
        String prompt = "以下是需要压缩的会话记录：\n\n" + bounded;
        ModelResponse response = llmClient.blockingChat(ChatRequest.builder().messages(List.of(
                        com.gacfox.proarc.agentic.model.openai.Message.builder()
                                .role(com.gacfox.proarc.agentic.model.openai.Message.ROLE_SYSTEM).content(recapSystemPrompt).build(),
                        com.gacfox.proarc.agentic.model.openai.Message.builder()
                                .role(com.gacfox.proarc.agentic.model.openai.Message.ROLE_USER).content(prompt).build()))
                .temperature(0.1).maxTokens(1200).build());
        String content = response.extractBlockingContent();
        if (content == null || content.isBlank()) throw new IllegalStateException("上下文摘要为空");
        return content;
    }

    private String batchText(ChatEventBatch batch, Map<Long, List<Message>> messagesByBatch) {
        StringBuilder text = new StringBuilder();
        text.append("=== Batch ").append(batch.getId()).append(" ===\n");
        text.append("【用户】\n").append(batch.getUserContent()).append("\n\n");
        text.append("【交互过程】\n");

        List<Message> messages = messagesByBatch.getOrDefault(batch.getId(), List.of());
        if (messages.isEmpty()) {
            text.append("（无助手回复）\n");
        }
        for (Message message : messages) {
            if ("assistant".equals(message.getRole())) {
                if (message.getContent() != null && !message.getContent().isBlank()) {
                    text.append("助手思考：").append(message.getContent()).append("\n");
                }
                if (message.getToolCallsJson() != null) {
                    try {
                        List<ToolCall> calls = OBJECT_MAPPER.readValue(message.getToolCallsJson(), new TypeReference<>() {
                        });
                        for (ToolCall call : calls) {
                            String name = call.getFunction().getName();
                            String args = call.getFunction().getArguments();
                            text.append("工具调用：").append(name);
                            if (args != null && !args.isBlank()) {
                                text.append("，参数：").append(args);
                            }
                            text.append("\n");
                        }
                    } catch (Exception ignored) {
                        text.append("工具调用：").append(message.getToolCallsJson()).append("\n");
                    }
                }
            } else if ("tool".equals(message.getRole())) {
                text.append("工具返回（callId=").append(message.getToolCallId()).append("）：");
                if (message.getContent() != null && !message.getContent().isBlank()) {
                    text.append(message.getContent());
                } else {
                    text.append("(无内容)");
                }
                text.append("\n");
            }
        }
        return text.toString().trim();
    }

    private Map<Long, List<Message>> messagesByBatch(Long conversationId) {
        Map<Long, List<Message>> result = new HashMap<>();
        for (Message message : messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)) {
            if (message.getBatchId() != null)
                result.computeIfAbsent(message.getBatchId(), ignored -> new ArrayList<>()).add(message);
        }
        return result;
    }

    private boolean shouldFoldToolMessage(Message message) {
        if ("tool".equals(message.getRole())) {
            return true;
        }
        if (message.getToolCallsJson() == null) {
            return false;
        }
        try {
            List<ToolCall> calls = OBJECT_MAPPER.readValue(message.getToolCallsJson(), new TypeReference<>() {
            });
            return calls.stream().noneMatch(call -> "final_answer".equals(call.getFunction().getName()));
        } catch (Exception ignored) {
            return true;
        }
    }

    private Set<Long> proactivelyCompressedBatchIds(List<ContextRecap> recaps, List<ChatEventBatch> batches) {
        Set<Long> ids = new HashSet<>();
        Map<Long, Integer> positions = new HashMap<>();
        for (int i = 0; i < batches.size(); i++) positions.put(batches.get(i).getId(), i);
        for (ContextRecap recap : recaps) {
            if (!"PROACTIVE".equals(recap.getType())) continue;
            Integer from = positions.get(recap.getFromBatchId());
            Integer to = positions.get(recap.getToBatchId());
            if (from == null || to == null) continue;
            for (int i = from; i <= to; i++) ids.add(batches.get(i).getId());
        }
        return ids;
    }

    private List<ChatEventBatch> userBatches(Long conversationId) {
        return batchRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(b -> (b.getType() == null || "USER".equals(b.getType())) && "COMPLETED".equals(b.getStatus()))
                .sorted(Comparator.comparing(ChatEventBatch::getCreatedAt)).toList();
    }

    private String truncateContent(String value) {
        if (value == null || value.codePointCount(0, value.length()) <= 100) return value;
        int end = value.offsetByCodePoints(0, 100);
        return value.substring(0, end) + TRUNCATED;
    }
}
