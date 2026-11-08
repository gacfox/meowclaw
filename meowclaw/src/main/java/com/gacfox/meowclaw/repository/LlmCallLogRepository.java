package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.LlmCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {
    List<LlmCallLog> findByBatchIdOrderByCreatedAtAsc(Long batchId);

    boolean existsByBatchIdAndPurpose(Long batchId, String purpose);

    @Modifying
    @Query("delete from LlmCallLog l where l.batchId in :batchIds")
    int deleteByBatchIdIn(@Param("batchIds") Collection<Long> batchIds);

    @Query("select l.batchId, count(l) from LlmCallLog l where l.batchId in :ids group by l.batchId")
    List<Object[]> countByBatchIdIn(@Param("ids") Collection<Long> ids);

    @Query("select distinct l.batchId from LlmCallLog l where l.batchId is not null"
            + " and not exists (select b.id from ChatEventBatch b where b.id = l.batchId and b.status = 'RUNNING')")
    List<Long> findNonRunningBatchIds();

    @Query("select distinct l.batchId from LlmCallLog l where l.batchId is not null and l.purpose = 'agent'"
            + " and l.createdAt < :cutoff and not exists (select b.id from ChatEventBatch b"
            + " where b.id = l.batchId and b.status = 'RUNNING') and not exists (select recent.id from LlmCallLog recent"
            + " where recent.batchId = l.batchId and recent.purpose = 'agent' and recent.createdAt >= :cutoff)")
    List<Long> findExpiredAgentBatchIdsWithoutRecentAgent(@Param("cutoff") Long cutoff);

    @Modifying
    @Query("delete from LlmCallLog l where not exists (select b.id from ChatEventBatch b"
            + " where b.id = l.batchId and b.status = 'RUNNING')")
    int deleteAllExceptRunning();

    @Modifying
    @Query("delete from LlmCallLog l where l.createdAt < :cutoff and not exists"
            + " (select b.id from ChatEventBatch b where b.id = l.batchId and b.status = 'RUNNING')")
    int deleteExpiredExceptRunning(@Param("cutoff") Long cutoff);
}
