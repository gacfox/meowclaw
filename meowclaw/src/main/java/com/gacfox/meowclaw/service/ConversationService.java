package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.ChatEventBatchConverter;
import com.gacfox.meowclaw.converter.ChatEventConverter;
import com.gacfox.meowclaw.converter.ConversationConverter;
import com.gacfox.meowclaw.dto.ChatEventBatchDTO;
import com.gacfox.meowclaw.dto.ConversationDTO;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ChatEventRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import com.gacfox.meowclaw.repository.ContextRecapRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gacfox.proarc.common.model.Pagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Service
public class ConversationService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatEventBatchRepository chatEventBatchRepository;
    private final ChatEventRepository chatEventRepository;
    private final ContextRecapRepository contextRecapRepository;
    private final ConversationConverter conversationConverter;
    private final ChatEventBatchConverter chatEventBatchConverter;
    private final ChatEventConverter chatEventConverter;

    @Autowired
    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               ChatEventBatchRepository chatEventBatchRepository,
                               ChatEventRepository chatEventRepository,
                               ContextRecapRepository contextRecapRepository,
                               ConversationConverter conversationConverter,
                               ChatEventBatchConverter chatEventBatchConverter,
                               ChatEventConverter chatEventConverter) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chatEventBatchRepository = chatEventBatchRepository;
        this.chatEventRepository = chatEventRepository;
        this.contextRecapRepository = contextRecapRepository;
        this.conversationConverter = conversationConverter;
        this.chatEventBatchConverter = chatEventBatchConverter;
        this.chatEventConverter = chatEventConverter;
    }

    @Transactional(readOnly = true)
    public Pagination<ConversationDTO> listByAgent(Long agentId, String type, int page, int size) {
        Page<Conversation> pageResult;
        if (type == null || type.isBlank()) {
            pageResult = conversationRepository.findByAgentIdOrderByUpdatedAtDesc(agentId, PageRequest.of(page - 1, size));
        } else {
            pageResult = conversationRepository.findByAgentIdAndTypeOrderByUpdatedAtDesc(agentId, type, PageRequest.of(page - 1, size));
        }
        List<ConversationDTO> list = pageResult.getContent().stream().map(conversationConverter::toDTO).toList();
        int total = (int) pageResult.getTotalElements();
        int totalPages = (int) Math.ceil((double) total / size);
        return new Pagination<>(list, total, totalPages, page, size);
    }

    @Transactional
    public ConversationDTO create(Long agentId, String type) {
        Conversation conv = new Conversation();
        conv.setAgentId(agentId);
        conv.setType(type);
        long now = System.currentTimeMillis();
        conv.setCreatedAt(now);
        conv.setUpdatedAt(now);
        return conversationConverter.toDTO(conversationRepository.save(conv));
    }

    @Transactional
    public void delete(Long id) {
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        List<Long> batchIds = chatEventBatchRepository.findByConversationIdOrderByCreatedAtAsc(id)
                .stream().map(ChatEventBatch::getId).toList();
        if (!batchIds.isEmpty()) {
            chatEventRepository.deleteByBatchIdIn(batchIds);
        }
        chatEventBatchRepository.deleteByConversationId(id);
        contextRecapRepository.deleteByConversationId(id);
        messageRepository.deleteByConversationId(id);
        conversationRepository.delete(conv);
    }

    @Transactional(readOnly = true)
    public List<ChatEventBatchDTO> listBatches(Long conversationId) {
        List<ChatEventBatch> batches = chatEventBatchRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        return batches.stream().map(batch -> {
            ChatEventBatchDTO dto = chatEventBatchConverter.toDTO(batch);
            dto.setEvents(chatEventRepository.findByBatchIdOrderByEventOrderAsc(batch.getId())
                    .stream().map(chatEventConverter::toDTO).toList());
            return dto;
        }).toList();
    }

    @Transactional
    public ConversationDTO updateTitle(Long id, String title) {
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        conv.setTitle(title);
        conv.setUpdatedAt(System.currentTimeMillis());
        return conversationConverter.toDTO(conversationRepository.save(conv));
    }

    @Transactional
    public void updateContextJson(Long id, String contextJson) {
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        conv.setContextJson(contextJson);
        conv.setUpdatedAt(System.currentTimeMillis());
        conversationRepository.save(conv);
    }

    @Transactional
    public void updateCwd(Long id, String cwd) {
        updateContextField(id, "cwd", cwd);
    }

    @Transactional
    public void updateContextHealth(Long id, long promptTokens, Integer contextLength, String status) {
        Conversation conv = getById(id);
        ObjectNode root = contextRoot(conv.getContextJson());
        ObjectNode health = root.putObject("context");
        health.put("promptTokens", promptTokens);
        if (contextLength != null) health.put("contextLength", contextLength);
        health.put("status", status);
        health.put("measuredAt", System.currentTimeMillis());
        saveContext(conv, root);
    }

    @Transactional
    public void clearContextHealth(Long id) {
        Conversation conv = getById(id);
        ObjectNode root = contextRoot(conv.getContextJson());
        ObjectNode health = root.putObject("context");
        health.put("promptTokens", 0);
        health.put("status", "NORMAL");
        health.put("measuredAt", System.currentTimeMillis());
        saveContext(conv, root);
    }

    @Transactional(readOnly = true)
    public String getContextStatus(Long id) {
        Conversation conv = getById(id);
        try {
            JsonNode node = OBJECT_MAPPER.readTree(conv.getContextJson());
            JsonNode status = node.path("context").path("status");
            return status.isTextual() ? status.asText() : "NORMAL";
        } catch (Exception e) {
            return "NORMAL";
        }
    }

    @Transactional(readOnly = true)
    public long getContextPromptTokens(Long id) {
        Conversation conv = getById(id);
        try {
            JsonNode node = OBJECT_MAPPER.readTree(conv.getContextJson());
            return node.path("context").path("promptTokens").asLong(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private void updateContextField(Long id, String name, String value) {
        Conversation conv = getById(id);
        ObjectNode root = contextRoot(conv.getContextJson());
        if (value == null) root.putNull(name); else root.put(name, value);
        saveContext(conv, root);
    }

    private ObjectNode contextRoot(String contextJson) {
        try {
            JsonNode node = contextJson == null ? null : OBJECT_MAPPER.readTree(contextJson);
            if (node instanceof ObjectNode objectNode) return objectNode;
        } catch (Exception ignored) {
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    private void saveContext(Conversation conv, ObjectNode root) {
        try {
            conv.setContextJson(OBJECT_MAPPER.writeValueAsString(root));
            conv.setUpdatedAt(System.currentTimeMillis());
            conversationRepository.save(conv);
        } catch (Exception e) {
            throw new IllegalStateException("更新会话上下文失败", e);
        }
    }

    @Transactional
    public ConversationDTO touch(Long id) {
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        conv.setUpdatedAt(System.currentTimeMillis());
        return conversationConverter.toDTO(conversationRepository.save(conv));
    }

    @Transactional(readOnly = true)
    public Conversation getById(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
    }

    @Transactional(readOnly = true)
    public ConversationDTO getDTO(Long id) {
        return conversationConverter.toDTO(getById(id));
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return conversationRepository.existsById(id);
    }

    @Transactional
    public void truncateAfterBatch(Long conversationId, Long batchId, boolean includeSelf) {
        List<ChatEventBatch> allBatches = chatEventBatchRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        Stream<ChatEventBatch> stream = allBatches.stream()
                .dropWhile(b -> !b.getId().equals(batchId));
        if (!includeSelf) {
            stream = stream.skip(1);
        }
        List<Long> batchIdsToDelete = stream.map(ChatEventBatch::getId).toList();
        if (batchIdsToDelete.isEmpty()) return;
        chatEventRepository.deleteByBatchIdIn(batchIdsToDelete);
        messageRepository.deleteByBatchIdIn(batchIdsToDelete);
        contextRecapRepository.deleteByConversationIdAndFromBatchIdInOrConversationIdAndToBatchIdIn(
                conversationId, batchIdsToDelete, conversationId, batchIdsToDelete);
        chatEventBatchRepository.deleteAllById(batchIdsToDelete);
        clearContextHealth(conversationId);
    }
}
