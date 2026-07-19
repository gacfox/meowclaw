package com.gacfox.meowclaw.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 标题生成在途状态：基于内存的 conversationId → future 映射。
 * 同一会话标题只在首轮生成一次（后续对话 title 已存在），future 实质一次性。
 * 60s 兜底超时防御 LLM 调用挂起导致内存泄漏。
 */
@Slf4j
@Service
public class TitleGenerationRegistryService {
    private static final long TIMEOUT_SECONDS = 60;
    private final Map<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<String> register(Long conversationId) {
        return pending.computeIfAbsent(conversationId, k -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            future.whenComplete((r, e) -> pending.remove(conversationId, future));
            return future;
        });
    }

    public CompletableFuture<String> get(Long conversationId) {
        return pending.get(conversationId);
    }

    public void complete(Long conversationId, String title) {
        CompletableFuture<String> future = pending.remove(conversationId);
        if (future != null) {
            future.complete(title);
        }
    }
}
