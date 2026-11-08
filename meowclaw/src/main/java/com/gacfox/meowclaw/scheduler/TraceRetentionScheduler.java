package com.gacfox.meowclaw.scheduler;

import com.gacfox.meowclaw.service.TraceRetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class TraceRetentionScheduler {
    private final TraceRetentionService traceRetentionService;
    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    public TraceRetentionScheduler(TraceRetentionService traceRetentionService) {
        this.traceRetentionService = traceRetentionService;
    }

    @Async
    public void cleanupAsync() {
        runCleanup();
    }

    @Scheduled(initialDelay = 60_000L, fixedDelay = 60 * 60 * 1000L)
    public void scheduledCleanup() {
        runCleanup();
    }

    private void runCleanup() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            int deleted = traceRetentionService.cleanupExpired();
            if (deleted > 0) {
                log.info("追踪留存清理完成，删除{}条调用明细", deleted);
            }
        } catch (Exception e) {
            log.error("追踪留存清理失败", e);
        } finally {
            running.set(false);
        }
    }
}
