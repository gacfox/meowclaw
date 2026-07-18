package com.gacfox.meowclaw.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class SchedulerManager {
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Autowired
    public SchedulerManager(ThreadPoolTaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

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
