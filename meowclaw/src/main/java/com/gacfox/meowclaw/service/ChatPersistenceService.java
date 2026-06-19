package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.ChatEvent;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.entity.Message;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ChatEventRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import com.gacfox.proarc.agentic.model.openai.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ChatPersistenceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatEventBatchRepository chatEventBatchRepository;
    private final ChatEventRepository chatEventRepository;
    private final MessageRepository messageRepository;
    private final ConversationService conversationService;

    public ChatPersistenceService(ChatEventBatchRepository chatEventBatchRepository,
                                  ChatEventRepository chatEventRepository,
                                  MessageRepository messageRepository,
                                  ConversationService conversationService) {
        this.chatEventBatchRepository = chatEventBatchRepository;
        this.chatEventRepository = chatEventRepository;
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
    }

    @Transactional
    public ChatEventBatch createBatch(Long conversationId, String userContent) {
        ChatEventBatch batch = new ChatEventBatch();
        batch.setConversationId(conversationId);
        batch.setUserContent(userContent);
        batch.setStatus("RUNNING");
        batch.setCreatedAt(System.currentTimeMillis());
        return chatEventBatchRepository.save(batch);
    }

    @Transactional
    public void saveChatEvent(Long batchId, int eventOrder, String type, String content,
                              String toolName, String toolCallId, String toolArguments) {
        ChatEvent chatEvent = new ChatEvent();
        chatEvent.setBatchId(batchId);
        chatEvent.setEventOrder(eventOrder);
        chatEvent.setType(type);
        chatEvent.setContent(content);
        chatEvent.setToolName(toolName);
        chatEvent.setToolCallId(toolCallId);
        chatEvent.setToolArguments(toolArguments);
        chatEvent.setCreatedAt(System.currentTimeMillis());
        chatEventRepository.save(chatEvent);
    }

    @Transactional
    public void completeBatch(Long batchId, Long conversationId,
                              List<com.gacfox.proarc.agentic.model.openai.Message> newMessages,
                              long inputTokens, long outputTokens) {
        for (var msg : newMessages) {
            saveContextMessage(batchId, conversationId, msg);
        }
        ChatEventBatch batch = chatEventBatchRepository.findById(batchId).orElseThrow();
        batch.setStatus("COMPLETED");
        batch.setCompletedAt(System.currentTimeMillis());
        batch.setInputTokens(inputTokens);
        batch.setOutputTokens(outputTokens);
        chatEventBatchRepository.save(batch);
        conversationService.touch(conversationId);
    }

    @Transactional
    public void failBatch(Long batchId, String errorMessage) {
        ChatEventBatch batch = chatEventBatchRepository.findById(batchId).orElseThrow();
        batch.setStatus("ERROR");
        batch.setErrorMessage(errorMessage);
        batch.setCompletedAt(System.currentTimeMillis());
        chatEventBatchRepository.save(batch);
    }

    private void saveContextMessage(Long batchId, Long conversationId,
                                    com.gacfox.proarc.agentic.model.openai.Message msg) {
        Message entity = new Message();
        entity.setBatchId(batchId);
        entity.setConversationId(conversationId);
        entity.setRole(msg.getRole());
        entity.setContent(msg.getContent() != null ? msg.getContent().toString() : "");
        entity.setReasoningContent(msg.getReasoningContent());
        entity.setToolCallId(msg.getToolCallId());
        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            try {
                entity.setToolCallsJson(OBJECT_MAPPER.writeValueAsString(msg.getToolCalls()));
            } catch (Exception e) {
                log.warn("Failed to serialize tool calls", e);
            }
        }
        entity.setCreatedAt(System.currentTimeMillis());
        messageRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public com.gacfox.proarc.agentic.model.openai.Message toProarcMessage(Message entity) {
        var builder = com.gacfox.proarc.agentic.model.openai.Message.builder()
                .role(entity.getRole())
                .content(entity.getContent());
        if (entity.getReasoningContent() != null) {
            builder.reasoningContent(entity.getReasoningContent());
        }
        if (entity.getToolCallId() != null) {
            builder.toolCallId(entity.getToolCallId());
        }
        if (entity.getToolCallsJson() != null) {
            try {
                List<ToolCall> toolCalls = OBJECT_MAPPER.readValue(
                        entity.getToolCallsJson(), new TypeReference<>() {});
                builder.toolCalls(toolCalls);
            } catch (Exception e) {
                log.warn("Failed to deserialize tool calls for message {}", entity.getId(), e);
            }
        }
        return builder.build();
    }
}
