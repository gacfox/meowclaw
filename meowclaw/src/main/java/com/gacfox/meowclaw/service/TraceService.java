package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.ChatEventConverter;
import com.gacfox.meowclaw.converter.LlmCallLogConverter;
import com.gacfox.meowclaw.dto.TraceDetailDTO;
import com.gacfox.meowclaw.dto.TraceItemDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ChatEventRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.LlmCallLogRepository;
import com.gacfox.proarc.common.model.Pagination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 追踪观测服务：批次级trace的列表与详情查询
 */
@Service
public class TraceService {

    private final ChatEventBatchRepository batchRepository;
    private final ChatEventRepository chatEventRepository;
    private final LlmCallLogRepository llmCallLogRepository;
    private final ConversationRepository conversationRepository;
    private final AgentRepository agentRepository;
    private final ChatEventConverter chatEventConverter;
    private final LlmCallLogConverter llmCallLogConverter;
    private final ContextCompressionService contextCompressionService;

    public TraceService(ChatEventBatchRepository batchRepository,
                        ChatEventRepository chatEventRepository,
                        LlmCallLogRepository llmCallLogRepository,
                        ConversationRepository conversationRepository,
                        AgentRepository agentRepository,
                        ChatEventConverter chatEventConverter,
                        LlmCallLogConverter llmCallLogConverter,
                        ContextCompressionService contextCompressionService) {
        this.batchRepository = batchRepository;
        this.chatEventRepository = chatEventRepository;
        this.llmCallLogRepository = llmCallLogRepository;
        this.conversationRepository = conversationRepository;
        this.agentRepository = agentRepository;
        this.chatEventConverter = chatEventConverter;
        this.llmCallLogConverter = llmCallLogConverter;
        this.contextCompressionService = contextCompressionService;
    }

    @Transactional(readOnly = true)
    public Pagination<TraceItemDTO> list(Long conversationId, Long agentId, String status, String keyword,
                                         Long startTime, Long endTime, int page, int size) {
        String queryStatus = (status == null || status.isBlank()) ? null : status;
        String queryKeyword = (keyword == null || keyword.isBlank()) ? null : "%" + keyword + "%";
        Page<ChatEventBatch> pageResult = batchRepository.searchTraces(
                conversationId, agentId, queryStatus, queryKeyword, startTime, endTime,
                PageRequest.of(page - 1, size));
        List<ChatEventBatch> batches = pageResult.getContent();

        List<Long> batchIds = batches.stream().map(ChatEventBatch::getId).toList();
        Map<Long, Long> callCounts = new HashMap<>();
        if (!batchIds.isEmpty()) {
            for (Object[] row : llmCallLogRepository.countByBatchIdIn(batchIds)) {
                callCounts.put((Long) row[0], (Long) row[1]);
            }
        }
        Map<Long, Conversation> conversationMap = conversationsOf(batches);
        Map<Long, String> agentNames = agentNamesOf(conversationMap);

        List<TraceItemDTO> list = batches.stream()
                .map(batch -> {
                    Conversation conv = conversationMap.get(batch.getConversationId());
                    return TraceItemDTO.builder()
                            .batchId(batch.getId())
                            .conversationId(batch.getConversationId())
                            .conversationTitle(conv != null ? conv.getTitle() : null)
                            .agentId(conv != null ? conv.getAgentId() : null)
                            .agentName(conv != null ? agentNames.get(conv.getAgentId()) : null)
                            .userContent(batch.getUserContent())
                            .status(batch.getStatus())
                            .inputTokens(batch.getInputTokens())
                            .outputTokens(batch.getOutputTokens())
                            .llmCallCount(callCounts.getOrDefault(batch.getId(), 0L))
                            .createdAt(batch.getCreatedAt())
                            .completedAt(batch.getCompletedAt())
                            .build();
                }).toList();
        int total = (int) pageResult.getTotalElements();
        int totalPages = (int) Math.ceil((double) total / size);
        return new Pagination<>(list, total, totalPages, page, size);
    }

    @Transactional(readOnly = true)
    public TraceDetailDTO detail(Long batchId) {
        ChatEventBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在"));
        Conversation conv = conversationRepository.findById(batch.getConversationId()).orElse(null);
        String agentName = conv != null
                ? agentRepository.findById(conv.getAgentId()).map(Agent::getName).orElse(null) : null;
        return TraceDetailDTO.builder()
                .batchId(batch.getId())
                .conversationId(batch.getConversationId())
                .conversationTitle(conv != null ? conv.getTitle() : null)
                .agentId(conv != null ? conv.getAgentId() : null)
                .agentName(agentName)
                .userContent(batch.getUserContent())
                .attachments(contextCompressionService.parseAttachments(batch.getAttachments()))
                .status(batch.getStatus())
                .errorMessage(batch.getErrorMessage())
                .inputTokens(batch.getInputTokens())
                .outputTokens(batch.getOutputTokens())
                .createdAt(batch.getCreatedAt())
                .completedAt(batch.getCompletedAt())
                .events(chatEventRepository.findByBatchIdOrderByEventOrderAsc(batchId).stream()
                        .map(chatEventConverter::toDTO).toList())
                .llmCalls(llmCallLogRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
                        .map(llmCallLogConverter::toDTO).toList())
                .build();
    }

    private Map<Long, Conversation> conversationsOf(List<ChatEventBatch> batches) {
        List<Long> conversationIds = batches.stream()
                .map(ChatEventBatch::getConversationId).distinct().toList();
        return conversationRepository.findAllById(conversationIds).stream()
                .collect(Collectors.toMap(Conversation::getId, c -> c));
    }

    private Map<Long, String> agentNamesOf(Map<Long, Conversation> conversationMap) {
        List<Long> agentIds = conversationMap.values().stream()
                .map(Conversation::getAgentId).distinct().toList();
        return agentRepository.findAllById(agentIds).stream()
                .collect(Collectors.toMap(Agent::getId, Agent::getName));
    }
}
