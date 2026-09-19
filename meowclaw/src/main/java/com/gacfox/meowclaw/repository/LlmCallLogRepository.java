package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.dto.TokenUsageStatsRow;
import com.gacfox.meowclaw.entity.LlmCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {

    List<LlmCallLog> findByBatchIdOrderByCreatedAtAsc(Long batchId);

    @Query("select l.batchId, count(l) from LlmCallLog l where l.batchId in :ids group by l.batchId")
    List<Object[]> countByBatchIdIn(@Param("ids") Collection<Long> ids);

    @Query("select new com.gacfox.meowclaw.dto.TokenUsageStatsRow("
            + "l.llmId, l.model, l.inputTokens, l.outputTokens, l.totalTokens, l.createdAt)"
            + " from LlmCallLog l where l.createdAt between :start and :end")
    List<TokenUsageStatsRow> findStatsRows(@Param("start") Long start, @Param("end") Long end);
}
