package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Long> {
}
