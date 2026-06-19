package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.ScheduledTaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduledTaskExecutionRepository extends JpaRepository<ScheduledTaskExecution, Long> {
    List<ScheduledTaskExecution> findByScheduledTaskIdOrderByExecutedAtDesc(Long scheduledTaskId);

    Optional<ScheduledTaskExecution> findTopByScheduledTaskIdOrderByExecutedAtDesc(Long scheduledTaskId);

    void deleteByScheduledTaskId(Long scheduledTaskId);
}
