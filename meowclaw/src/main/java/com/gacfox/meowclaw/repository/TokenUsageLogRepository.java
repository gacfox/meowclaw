package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.TokenUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TokenUsageLogRepository extends JpaRepository<TokenUsageLog, Long> {

    List<TokenUsageLog> findByCreatedAtBetween(Long start, Long end);
}
