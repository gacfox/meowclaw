package com.gacfox.meowclaw.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 当前进程内的追踪删除标记与写入协调。
 * 重启后无需保留：重启前尚未完成的异步写入不会跨进程继续执行。
 */
@Component
public class TraceDeletionRegistry {
    private final Set<Long> deletedBatchIds = ConcurrentHashMap.newKeySet();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public <T> T writeIfNotDeleted(Long batchId, Supplier<T> writer) {
        lock.readLock().lock();
        try {
            if (batchId != null && deletedBatchIds.contains(batchId)) {
                return null;
            }
            return writer.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> T delete(Collection<Long> batchIds, Supplier<T> deleter) {
        lock.writeLock().lock();
        try {
            deletedBatchIds.addAll(batchIds);
            try {
                return deleter.get();
            } catch (RuntimeException e) {
                deletedBatchIds.removeAll(batchIds);
                throw e;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
