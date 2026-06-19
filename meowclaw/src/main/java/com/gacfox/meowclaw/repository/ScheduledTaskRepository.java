package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.ScheduledTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {
    List<ScheduledTask> findByEnabledTrue();
}
