package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.Pagination;
import com.gacfox.meowclaw.dto.TraceItemDTO;
import com.gacfox.meowclaw.dto.TraceRetentionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.*;
import com.gacfox.meowclaw.interceptor.llm.TokenUsageContext;
import com.gacfox.meowclaw.repository.*;
import com.gacfox.meowclaw.util.JsonUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always"
})
class TraceManagementServiceTest {
    @Autowired
    private ChatEventBatchRepository batchRepository;
    @Autowired
    private ChatEventRepository chatEventRepository;
    @Autowired
    private LlmCallLogRepository llmCallLogRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private ContextRecapRepository contextRecapRepository;
    @Autowired
    private TokenUsageRepository tokenUsageRepository;
    @Autowired
    private LlmRepository llmRepository;
    @Autowired
    private SysConfigRepository sysConfigRepository;
    @Autowired
    private EntityManager entityManager;

    private TraceService traceService;
    private TraceDeletionRegistry traceDeletionRegistry;

    @BeforeEach
    void setUp() {
        traceDeletionRegistry = new TraceDeletionRegistry();
        traceService = new TraceService(batchRepository, chatEventRepository, llmCallLogRepository,
                conversationRepository, agentRepository, null, null, null, traceDeletionRegistry);
    }

    @Test
    void listOnlyShowsBatchesWithMainAgentTrace() {
        ChatEventBatch traced = batch("COMPLETED", System.currentTimeMillis());
        ChatEventBatch withoutLog = batch("COMPLETED", System.currentTimeMillis());
        ChatEventBatch auxiliaryOnly = batch("COMPLETED", System.currentTimeMillis());
        log(traced.getId(), "agent", System.currentTimeMillis());
        log(auxiliaryOnly.getId(), "title", System.currentTimeMillis());

        Pagination<TraceItemDTO> result = traceService.list(null, null, null, null,
                null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).extracting(TraceItemDTO::getBatchId).containsExactly(traced.getId());
        assertThatThrownBy(() -> traceService.detail(withoutLog.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("追踪不存在");
    }

    @Test
    void deleteTracePreservesConversationDataAndTokenUsage() {
        ChatEventBatch batch = batch("COMPLETED", System.currentTimeMillis());
        log(batch.getId(), "agent", System.currentTimeMillis());
        log(batch.getId(), "title", System.currentTimeMillis());

        ChatEvent event = new ChatEvent();
        event.setBatchId(batch.getId());
        event.setEventOrder(0);
        event.setType("content");
        event.setContent("回答");
        event.setCreatedAt(System.currentTimeMillis());
        chatEventRepository.save(event);

        Message message = new Message();
        message.setConversationId(batch.getConversationId());
        message.setBatchId(batch.getId());
        message.setRole("assistant");
        message.setContent("回答");
        message.setCreatedAt(System.currentTimeMillis());
        messageRepository.save(message);

        ContextRecap recap = new ContextRecap();
        recap.setConversationId(batch.getConversationId());
        recap.setFromBatchId(batch.getId());
        recap.setToBatchId(batch.getId());
        recap.setType("ROLLING");
        recap.setContent("摘要");
        recap.setCreatedAt(System.currentTimeMillis());
        contextRecapRepository.save(recap);
        tokenUsage(batch.getCreatedAt());

        traceService.delete(batch.getId());
        entityManager.clear();

        assertThat(llmCallLogRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())).isEmpty();
        assertThat(batchRepository.findById(batch.getId())).isPresent();
        assertThat(chatEventRepository.findByBatchIdOrderByEventOrderAsc(batch.getId())).hasSize(1);
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(batch.getConversationId())).hasSize(1);
        assertThat(contextRecapRepository.findByConversationIdOrderByCreatedAtAsc(batch.getConversationId())).hasSize(1);
        assertThat(tokenUsageRepository.count()).isEqualTo(1);
        var stats = new TokenUsageService(tokenUsageRepository, llmRepository)
                .stats(batch.getCreatedAt() - 1, batch.getCreatedAt() + 1, null);
        assertThat(stats.getSummary().getTotalTokens()).isEqualTo(3);
        assertThat(stats.getSummary().getCallCount()).isEqualTo(1);
        assertThatThrownBy(() -> traceService.detail(batch.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletedTraceCannotReappearThroughPendingAsyncWrite() {
        ChatEventBatch batch = batch("COMPLETED", System.currentTimeMillis());
        log(batch.getId(), "agent", batch.getCreatedAt());
        assertThat(traceService.delete(batch.getId())).isEqualTo(1);

        TraceObservationService observationService = new TraceObservationService(
                new SystemConfigService(sysConfigRepository));
        LlmCallLogService logService = new LlmCallLogService(llmCallLogRepository,
                new JsonUtil(new ObjectMapper()), traceDeletionRegistry, observationService);
        TokenUsageContext context = new TokenUsageContext(null, null, batch.getConversationId(),
                batch.getId(), "test-model", "agent");
        logService.recordCall(context, "[]", null, 1, "SUCCESS", null);
        entityManager.clear();

        assertThat(llmCallLogRepository.findByBatchIdOrderByCreatedAtAsc(batch.getId())).isEmpty();
        assertThat(traceService.list(null, null, null, null, null, null, 1, 20).getTotal()).isZero();
    }

    @Test
    void clearAllSkipsRunningAndCleansUnboundAndOrphanLogs() {
        ChatEventBatch completed = batch("COMPLETED", System.currentTimeMillis());
        ChatEventBatch running = batch("RUNNING", System.currentTimeMillis());
        log(completed.getId(), "agent", completed.getCreatedAt());
        log(running.getId(), "agent", running.getCreatedAt());
        log(null, "memory", System.currentTimeMillis());
        log(999999L, "agent", System.currentTimeMillis());

        traceService.deleteAll();
        entityManager.clear();

        List<LlmCallLog> remaining = llmCallLogRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getBatchId()).isEqualTo(running.getId());
    }

    @Test
    void retentionDeletesOnlyExpiredNonRunningLogs() {
        long now = System.currentTimeMillis();
        long cutoff = now - 1000;
        ChatEventBatch oldCompleted = batch("COMPLETED", cutoff - 1000);
        ChatEventBatch recentCompleted = batch("COMPLETED", now);
        ChatEventBatch oldRunning = batch("RUNNING", cutoff - 1000);
        log(oldCompleted.getId(), "agent", cutoff - 1);
        log(recentCompleted.getId(), "agent", cutoff + 1);
        log(oldRunning.getId(), "agent", cutoff - 1);

        int deleted = traceService.deleteExpired(cutoff);
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(llmCallLogRepository.existsByBatchIdAndPurpose(oldCompleted.getId(), "agent")).isFalse();
        assertThat(llmCallLogRepository.existsByBatchIdAndPurpose(recentCompleted.getId(), "agent")).isTrue();
        assertThat(llmCallLogRepository.existsByBatchIdAndPurpose(oldRunning.getId(), "agent")).isTrue();
    }

    @Test
    void traceObservationDefaultsToEnabledAndPersistsSelection() {
        TraceObservationService observationService = new TraceObservationService(
                new SystemConfigService(sysConfigRepository));

        assertThat(observationService.isEnabled()).isTrue();
        observationService.updateEnabled(false);
        entityManager.flush();
        entityManager.clear();
        assertThat(observationService.isEnabled()).isFalse();
        observationService.updateEnabled(true);
        entityManager.flush();
        entityManager.clear();
        assertThat(observationService.isEnabled()).isTrue();
    }

    @Test
    void retentionPolicyDefaultsToAllAndPersistsSelection() {
        SystemConfigService configService = new SystemConfigService(sysConfigRepository);
        TraceRetentionService retentionService = new TraceRetentionService(configService, traceService);

        assertThat(retentionService.getPolicy()).isEqualTo(TraceRetentionPolicy.ALL);
        retentionService.updatePolicy(TraceRetentionPolicy.DAYS_7);
        entityManager.clear();
        assertThat(retentionService.getPolicy()).isEqualTo(TraceRetentionPolicy.DAYS_7);

        long now = Instant.parse("2026-11-08T00:00:00Z").toEpochMilli();
        assertThat(retentionService.cutoff(TraceRetentionPolicy.DAYS_1, now, ZoneId.of("UTC")))
                .isEqualTo(Instant.parse("2026-11-07T00:00:00Z").toEpochMilli());
        assertThat(retentionService.cutoff(TraceRetentionPolicy.DAYS_3, now, ZoneId.of("UTC")))
                .isEqualTo(Instant.parse("2026-11-05T00:00:00Z").toEpochMilli());
        assertThat(retentionService.cutoff(TraceRetentionPolicy.MONTH_1, now, ZoneId.of("UTC")))
                .isEqualTo(Instant.parse("2026-10-08T00:00:00Z").toEpochMilli());
        assertThat(retentionService.cutoff(TraceRetentionPolicy.YEAR_1, now, ZoneId.of("UTC")))
                .isEqualTo(Instant.parse("2025-11-08T00:00:00Z").toEpochMilli());
    }

    private ChatEventBatch batch(String status, long createdAt) {
        ChatEventBatch batch = new ChatEventBatch();
        batch.setConversationId(1L);
        batch.setUserContent("用户输入");
        batch.setType("USER");
        batch.setStatus(status);
        batch.setCreatedAt(createdAt);
        if (!"RUNNING".equals(status)) {
            batch.setCompletedAt(createdAt + 100);
        }
        return batchRepository.save(batch);
    }

    private LlmCallLog log(Long batchId, String purpose, long createdAt) {
        LlmCallLog log = new LlmCallLog();
        log.setBatchId(batchId);
        log.setModel("test-model");
        log.setPurpose(purpose);
        log.setRequestMessages("[]");
        log.setInputTokens(1L);
        log.setOutputTokens(2L);
        log.setTotalTokens(3L);
        log.setStatus("SUCCESS");
        log.setCreatedAt(createdAt);
        return llmCallLogRepository.save(log);
    }

    private void tokenUsage(long createdAt) {
        TokenUsage usage = new TokenUsage();
        usage.setModel("test-model");
        usage.setPurpose("agent");
        usage.setStatus("SUCCESS");
        usage.setInputTokens(1L);
        usage.setOutputTokens(2L);
        usage.setTotalTokens(3L);
        usage.setCreatedAt(createdAt);
        tokenUsageRepository.save(usage);
    }
}
