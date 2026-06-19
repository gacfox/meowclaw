package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.ScheduledTaskConverter;
import com.gacfox.meowclaw.converter.ScheduledTaskExecutionConverter;
import com.gacfox.meowclaw.dto.CreateScheduledTaskRequest;
import com.gacfox.meowclaw.dto.ScheduledTaskDTO;
import com.gacfox.meowclaw.dto.ScheduledTaskExecutionDTO;
import com.gacfox.meowclaw.dto.UpdateScheduledTaskRequest;
import com.gacfox.meowclaw.entity.ScheduledTask;
import com.gacfox.meowclaw.repository.ScheduledTaskExecutionRepository;
import com.gacfox.meowclaw.repository.ScheduledTaskRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ScheduledTaskService {
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final ScheduledTaskExecutionRepository scheduledTaskExecutionRepository;
    private final ScheduledTaskConverter scheduledTaskConverter;
    private final ScheduledTaskExecutionConverter scheduledTaskExecutionConverter;
    private final SchedulerManager schedulerManager;
    private final ScheduledTaskExecutor scheduledTaskExecutor;

    public ScheduledTaskService(ScheduledTaskRepository scheduledTaskRepository,
                                ScheduledTaskExecutionRepository scheduledTaskExecutionRepository,
                                ScheduledTaskConverter scheduledTaskConverter,
                                ScheduledTaskExecutionConverter scheduledTaskExecutionConverter,
                                SchedulerManager schedulerManager,
                                ScheduledTaskExecutor scheduledTaskExecutor) {
        this.scheduledTaskRepository = scheduledTaskRepository;
        this.scheduledTaskExecutionRepository = scheduledTaskExecutionRepository;
        this.scheduledTaskConverter = scheduledTaskConverter;
        this.scheduledTaskExecutionConverter = scheduledTaskExecutionConverter;
        this.schedulerManager = schedulerManager;
        this.scheduledTaskExecutor = scheduledTaskExecutor;
    }

    @PostConstruct
    public void init() {
        scheduledTaskRepository.findByEnabledTrue().forEach(task -> {
            try {
                Long taskId = task.getId();
                schedulerManager.schedule(taskId, task.getCronExpression(),
                        () -> scheduledTaskExecutor.execute(taskId));
                log.info("Loaded scheduled task {} on startup", taskId);
            } catch (Exception e) {
                log.error("Failed to load scheduled task {} on startup", task.getId(), e);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<ScheduledTaskDTO> list() {
        return scheduledTaskRepository.findAll().stream().map(scheduledTaskConverter::toDTO).toList();
    }

    @Transactional
    public ScheduledTaskDTO create(CreateScheduledTaskRequest req) {
        validateCron(req.getCronExpression());

        ScheduledTask task = new ScheduledTask();
        task.setName(req.getName());
        task.setAgentId(req.getAgentId());
        task.setUserPrompt(req.getUserPrompt());
        task.setCronExpression(req.getCronExpression());
        task.setCreateNewSession(Boolean.TRUE.equals(req.getCreateNewSession()));
        task.setEnabled(true);
        long now = System.currentTimeMillis();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task = scheduledTaskRepository.save(task);

        Long taskId = task.getId();
        schedulerManager.schedule(taskId, task.getCronExpression(),
                () -> scheduledTaskExecutor.execute(taskId));

        return scheduledTaskConverter.toDTO(task);
    }

    @Transactional
    public ScheduledTaskDTO update(Long id, UpdateScheduledTaskRequest req) {
        ScheduledTask task = scheduledTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));

        if (req.getName() != null) task.setName(req.getName());
        if (req.getAgentId() != null) task.setAgentId(req.getAgentId());
        if (req.getUserPrompt() != null) task.setUserPrompt(req.getUserPrompt());
        if (req.getCronExpression() != null) {
            validateCron(req.getCronExpression());
            task.setCronExpression(req.getCronExpression());
        }
        if (req.getCreateNewSession() != null) task.setCreateNewSession(req.getCreateNewSession());
        task.setUpdatedAt(System.currentTimeMillis());

        task = scheduledTaskRepository.save(task);

        if (task.getEnabled()) {
            Long taskId = task.getId();
            schedulerManager.schedule(taskId, task.getCronExpression(),
                    () -> scheduledTaskExecutor.execute(taskId));
        }

        return scheduledTaskConverter.toDTO(task);
    }

    @Transactional
    public void delete(Long id) {
        schedulerManager.cancel(id);
        scheduledTaskExecutionRepository.deleteByScheduledTaskId(id);
        scheduledTaskRepository.deleteById(id);
    }

    @Transactional
    public ScheduledTaskDTO toggleEnabled(Long id) {
        ScheduledTask task = scheduledTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));

        task.setEnabled(!task.getEnabled());
        task.setUpdatedAt(System.currentTimeMillis());
        task = scheduledTaskRepository.save(task);

        if (task.getEnabled()) {
            Long taskId = task.getId();
            schedulerManager.schedule(taskId, task.getCronExpression(),
                    () -> scheduledTaskExecutor.execute(taskId));
        } else {
            schedulerManager.cancel(task.getId());
        }

        return scheduledTaskConverter.toDTO(task);
    }

    public void triggerOnce(Long id) {
        scheduledTaskExecutor.execute(id);
    }

    @Transactional(readOnly = true)
    public List<ScheduledTaskExecutionDTO> listExecutions(Long taskId) {
        return scheduledTaskExecutionRepository.findByScheduledTaskIdOrderByExecutedAtDesc(taskId)
                .stream().map(scheduledTaskExecutionConverter::toDTO).toList();
    }

    private void validateCron(String cronExpression) {
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException("无效的Cron表达式: " + cronExpression);
        }
    }
}
