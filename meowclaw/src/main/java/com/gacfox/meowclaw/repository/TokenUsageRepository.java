package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.dto.TokenUsageStatsRow;
import com.gacfox.meowclaw.entity.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {
    @Query("select new com.gacfox.meowclaw.dto.TokenUsageStatsRow("
            + "t.llmId, t.model, t.inputTokens, t.outputTokens, t.totalTokens, t.createdAt)"
            + " from TokenUsage t where t.createdAt between :start and :end")
    List<TokenUsageStatsRow> findStatsRows(@Param("start") Long start, @Param("end") Long end);
}
