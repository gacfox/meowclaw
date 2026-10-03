package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.ChatEventBatchConverter;
import com.gacfox.meowclaw.converter.ChatEventConverter;
import com.gacfox.meowclaw.converter.ConversationConverter;
import com.gacfox.meowclaw.converter.ConversationHistoryConverter;
import com.gacfox.meowclaw.converter.ProjectConverter;
import com.gacfox.meowclaw.dto.ChatEventBatchDTO;
import com.gacfox.meowclaw.dto.ConversationDTO;
import com.gacfox.meowclaw.dto.ConversationHistoryDTO;
import com.gacfox.meowclaw.dto.ProjectDTO;
import com.gacfox.meowclaw.dto.SidebarGroupsDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.Project;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ChatEventRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.ContextRecapRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import com.gacfox.meowclaw.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gacfox.meowclaw.dto.Pagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class ConversationService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatEventBatchRepository chatEventBatchRepository;
    private final ChatEventRepository chatEventRepository;
    private final ContextRecapRepository contextRecapRepository;
    private final AgentRepository agentRepository;
    private final ConversationConverter conversationConverter;
    private final ConversationHistoryConverter conversationHistoryConverter;
    private final ChatEventBatchConverter chatEventBatchConverter;
    private final ChatEventConverter chatEventConverter;
    private final GuardrailPolicyService guardrailPolicyService;
    private final ProjectRepository projectRepository;
    private final ProjectConverter projectConverter;

    @Autowired
    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               ChatEventBatchRepository chatEventBatchRepository,
                               ChatEventRepository chatEventRepository,
                               ContextRecapRepository contextRecapRepository,
                               AgentRepository agentRepository,
                               ConversationConverter conversationConverter,
                               ConversationHistoryConverter conversationHistoryConverter,
                               ChatEventBatchConverter chatEventBatchConverter,
                               ChatEventConverter chatEventConverter,
                               GuardrailPolicyService guardrailPolicyService,
                               ProjectRepository projectRepository,
                               ProjectConverter projectConverter) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chatEventBatchRepository = chatEventBatchRepository;
        this.chatEventRepository = chatEventRepository;
        this.contextRecapRepository = contextRecapRepository;
        this.agentRepository = agentRepository;
        this.conversationConverter = conversationConverter;
        this.conversationHistoryConverter = conversationHistoryConverter;
        this.chatEventBatchConverter = chatEventBatchConverter;
        this.chatEventConverter = chatEventConverter;
        this.guardrailPolicyService = guardrailPolicyService;
        this.projectRepository = projectRepository;
        this.projectConverter = projectConverter;
    }

    /**
     * 实体转DTO，未显式指定安全护栏策略时回填生效的默认策略ID（定时任务会话默认无限制模式）
     */
    private ConversationDTO toDTO(Conversation conv) {
        ConversationDTO dto = conversationConverter.toDTO(conv);
        if (dto.getGuardrailPolicyId() == null) {
            dto.setGuardrailPolicyId(guardrailPolicyService.getEffectiveDefaultPolicyId(conv.getType()));
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public Pagination<ConversationDTO> listByAgent(Long agentId, String type, int page, int size) {
        return listByAgent(agentId, type, false, page, size);
    }

    @Transactional(readOnly = true)
    public Pagination<ConversationDTO> listByAgent(Long agentId, String type, boolean ungrouped, int page, int size) {
        Page<Conversation> pageResult;
        if (ungrouped) {
            pageResult = conversationRepository.findByAgentIdAndPinnedFalseAndProjectIdIsNullAndTypeNotOrderByUpdatedAtDesc(
                    agentId, "SUBAGENT", PageRequest.of(page - 1, size));
        } else if (type == null || type.isBlank()) {
            pageResult = conversationRepository.findByAgentIdAndTypeNotOrderByUpdatedAtDesc(agentId, "SUBAGENT", PageRequest.of(page - 1, size));
        } else {
            pageResult = conversationRepository.findByAgentIdAndTypeOrderByUpdatedAtDesc(agentId, type, PageRequest.of(page - 1, size));
        }
        List<ConversationDTO> list = pageResult.getContent().stream().map(this::toDTO).toList();
        int total = (int) pageResult.getTotalElements();
        int totalPages = (int) Math.ceil((double) total / size);
        return new Pagination<>(list, total, totalPages, page, size);
    }

    /**
     * 获取父会话ID，非子智能体会话返回null
     */
    @Transactional(readOnly = true)
    public Long getParentConversationId(Long id) {
        Conversation conv = conversationRepository.findById(id).orElse(null);
        return conv == null ? null : conv.getParentConversationId();
    }

    @Transactional(readOnly = true)
    public Pagination<ConversationHistoryDTO> listHistory(String type, Long agentId, String keyword, Long startTime, Long endTime, int page, int size) {
        String queryType = type == null || type.isBlank() ? null : type;
        String queryKeyword = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
        Page<Conversation> pageResult = conversationRepository.findHistory(
                queryType, agentId, queryKeyword, startTime, endTime, PageRequest.of(page - 1, size));
        List<Long> agentIds = pageResult.getContent().stream().map(Conversation::getAgentId).distinct().toList();
        Map<Long, String> agentNameMap = agentRepository.findAllById(agentIds).stream()
                .collect(java.util.stream.Collectors.toMap(Agent::getId, Agent::getName));
        List<ConversationHistoryDTO> list = pageResult.getContent().stream()
                .map(c -> conversationHistoryConverter.toDTO(c, agentNameMap.getOrDefault(c.getAgentId(), "")))
                .toList();
        int total = (int) pageResult.getTotalElements();
        int totalPages = (int) Math.ceil((double) total / size);
        return new Pagination<>(list, total, totalPages, page, size);
    }

    @Transactional
    public void deleteBatch(List<Long> ids) {
        ids.forEach(this::delete);
    }

    @Transactional
    public void deleteAll() {
        conversationRepository.findAll().stream().map(Conversation::getId).toList().forEach(this::delete);
    }

    @Transactional
    public ConversationDTO create(Long agentId, String type) {
        return create(agentId, type, null);
    }

    @Transactional
    public ConversationDTO create(Long agentId, String type, Long projectId) {
        Conversation conv = new Conversation();
        conv.setAgentId(agentId);
        conv.setType(type);
        conv.setProjectId(projectId);
        long now = System.currentTimeMillis();
        conv.setCreatedAt(now);
        conv.setUpdatedAt(now);
        return toDTO(conversationRepository.save(conv));
    }

    @Transactional
    public ConversationDTO pin(Long id, boolean pinned) {
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        conv.setPinned(pinned);
        return toDTO(conversationRepository.save(conv));
    }

    @Transactional
    public ConversationDTO moveToProject(Long id, Long projectId) {
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
            if (!project.getAgentId().equals(conv.getAgentId())) {
                throw new IllegalArgumentException("项目与会话不属于同一智能体");
            }
        }
        conv.setProjectId(projectId);
        return toDTO(conversationRepository.save(conv));
    }

    /**
     * 会话侧栏分组数据：钉选会话与项目（含项目内会话），均为全量小集合
     */
    @Transactional(readOnly = true)
    public SidebarGroupsDTO sidebarGroups(Long agentId) {
        List<ConversationDTO> pinned = conversationRepository
                .findByAgentIdAndPinnedTrueAndTypeNotOrderByUpdatedAtDesc(agentId, "SUBAGENT")
                .stream().map(this::toDTO).toList();
        List<ProjectDTO> projects = projectRepository.findByAgentIdOrderByCreatedAtAsc(agentId).stream()
                .map(p -> {
                    ProjectDTO dto = projectConverter.toDTO(p);
                    dto.setConversations(conversationRepository
                            .findByProjectIdAndTypeNotOrderByUpdatedAtDesc(p.getId(), "SUBAGENT")
                            .stream().map(this::toDTO).toList());
                    return dto;
                }).toList();
        return new SidebarGroupsDTO(pinned, projects);
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
        return toDTO(conversationRepository.save(conv));
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
        return toDTO(conversationRepository.save(conv));
    }

    @Transactional(readOnly = true)
    public Conversation getById(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
    }

    @Transactional(readOnly = true)
    public ConversationDTO getDTO(Long id) {
        return toDTO(getById(id));
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
