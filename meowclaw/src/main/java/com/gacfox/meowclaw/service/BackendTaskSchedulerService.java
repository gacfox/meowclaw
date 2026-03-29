package com.gacfox.meowclaw.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.ScheduledTask;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.ScheduledTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
public class BackendTaskSchedulerService {
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final TaskScheduler taskScheduler;
    private final ConversationExecutionService conversationExecutionService;
    private final CronParser cronParser;
    private final Map<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    public BackendTaskSchedulerService(ConversationService conversationService,
                                       ConversationRepository conversationRepository,
                                       ScheduledTaskRepository scheduledTaskRepository,
                                       TaskScheduler taskScheduler,
                                       ConversationExecutionService conversationExecutionService) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.scheduledTaskRepository = scheduledTaskRepository;
        this.taskScheduler = taskScheduler;
        this.conversationExecutionService = conversationExecutionService;
        this.cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleEnabledTasks() {
        List<ScheduledTask> enabledTasks = scheduledTaskRepository.findByEnabled(true);
        for (ScheduledTask task : enabledTasks) {
            scheduleOrReschedule(task);
        }
    }

    public void scheduleOrReschedule(ScheduledTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        if (!task.isEnabled()) {
            cancelTask(task.getId());
            return;
        }
        cancelTask(task.getId());
        try {
            Trigger trigger = buildTrigger(task.getCronExpression(), Instant.now().plusSeconds(1));
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeTaskById(task.getId()), trigger);
            if (future != null) {
                scheduledFutures.put(task.getId(), future);
            }
        } catch (Exception e) {
            log.error("注册定时任务失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
        }
    }

    public void cancelTask(Long taskId) {
        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            boolean ignored = future.cancel(false);
        }
    }

    public void triggerTask(ScheduledTask task) {
        executeTask(task);
        scheduledTaskRepository.updateLastExecutedAt(task.getId(), Instant.now().toEpochMilli());
    }

    private void executeTaskById(Long taskId) {
        Optional<ScheduledTask> taskOpt = scheduledTaskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            cancelTask(taskId);
            return;
        }
        ScheduledTask task = taskOpt.get();
        if (!task.isEnabled()) {
            return;
        }
        executeTask(task);
        scheduledTaskRepository.updateLastExecutedAt(taskId, Instant.now().toEpochMilli());
    }

    private void executeTask(ScheduledTask task) {
        log.info("执行定时任务: {} ({})", task.getName(), task.getId());

        Long conversationId = resolveConversationId(task);
        if (conversationId == null || conversationId <= 0) {
            log.error("定时任务没有有效的会话: taskId={}", task.getId());
            return;
        }

        try {
            conversationExecutionService.execute(conversationId, task.getUserPrompt());
        } catch (Exception e) {
            log.error("定时任务执行智能体失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
        }
    }

    private Long resolveConversationId(ScheduledTask task) {
        if (task.isNewSessionEach()) {
            Conversation conversation = conversationService.createScheduledConversation(task.getAgentConfigId());
            return conversation.getId();
        }

        Long boundId = task.getBoundConversationId();
        if (boundId == null || boundId <= 0) {
            return createAndBindConversation(task);
        }

        Optional<Conversation> conversationOpt = conversationRepository.findById(boundId);
        if (conversationOpt.isEmpty()) {
            return createAndBindConversation(task);
        }
        return boundId;
    }

    private Long createAndBindConversation(ScheduledTask task) {
        Conversation conversation = conversationService.createScheduledConversation(task.getAgentConfigId());
        Long conversationId = conversation.getId();
        task.setBoundConversationId(conversationId);
        scheduledTaskRepository.updateBoundConversationId(task.getId(), conversationId);
        log.info("绑定新会话: taskId={}, conversationId={}", task.getId(), conversationId);
        return conversationId;
    }

    private Trigger buildTrigger(String cronExpression, Instant initialBaseInstant) {
        Cron cron = cronParser.parse(cronExpression);
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        return new CronUtilsTrigger(executionTime, ZoneId.systemDefault(), initialBaseInstant);
    }

    private static class CronUtilsTrigger implements Trigger {
        private final ExecutionTime executionTime;
        private final ZoneId zoneId;
        private final Instant initialBaseInstant;

        private CronUtilsTrigger(ExecutionTime executionTime, ZoneId zoneId, Instant initialBaseInstant) {
            this.executionTime = executionTime;
            this.zoneId = zoneId;
            this.initialBaseInstant = initialBaseInstant;
        }

        @Override
        public Instant nextExecution(TriggerContext triggerContext) {
            Instant baseInstant = Optional.ofNullable(triggerContext.lastCompletion())
                    .or(() -> Optional.ofNullable(triggerContext.lastScheduledExecution()))
                    .or(() -> Optional.ofNullable(triggerContext.lastActualExecution()))
                    .orElse(initialBaseInstant);
            ZonedDateTime baseTime = baseInstant.atZone(zoneId);
            Optional<ZonedDateTime> next = executionTime.nextExecution(baseTime);
            return next.map(ZonedDateTime::toInstant).orElse(null);
        }
    }
}
