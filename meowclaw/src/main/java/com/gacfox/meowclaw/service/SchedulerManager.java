package com.gacfox.meowclaw.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class SchedulerManager {
    private final TaskScheduler taskScheduler = new ConcurrentTaskScheduler();
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public void schedule(Long taskId, String cronExpression, Runnable runnable) {
        cancel(taskId);
        ScheduledFuture<?> future = taskScheduler.schedule(runnable, new CronTrigger(cronExpression));
        scheduledTasks.put(taskId, future);
        log.info("Scheduled task {} with cron: {}", taskId, cronExpression);
    }

    public void cancel(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("Cancelled scheduled task {}", taskId);
        }
    }
}
