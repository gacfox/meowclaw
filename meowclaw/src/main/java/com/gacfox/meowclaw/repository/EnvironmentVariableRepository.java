package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.EnvironmentVariable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, Long> {
    List<EnvironmentVariable> findByAgentIdIsNullOrderByKeyNameAsc();

    List<EnvironmentVariable> findByAgentIdOrderByKeyNameAsc(Long agentId);

    Optional<EnvironmentVariable> findByAgentIdIsNullAndKeyNameIgnoreCase(String keyName);

    Optional<EnvironmentVariable> findByAgentIdAndKeyNameIgnoreCase(Long agentId, String keyName);

    void deleteByAgentId(Long agentId);
}
