package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByAgentIdOrderByCreatedAtAsc(Long agentId);

    boolean existsByAgentIdAndName(Long agentId, String name);
}
