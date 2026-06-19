package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.McpServiceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface McpServiceConfigRepository extends JpaRepository<McpServiceConfig, Long> {
    Optional<McpServiceConfig> findByName(String name);

    List<McpServiceConfig> findByEnabledTrue();
}
