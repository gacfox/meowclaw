package com.gacfox.meowclaw.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 子智能体运行注册表：父会话ID到其子任务句柄列表的映射，与父运行同生命周期
 */
@Component
public class SubagentRegistry {

    /**
     * 子任务句柄
     *
     * @param childConversationId 子会话ID
     * @param eventId             父批次中subagent事件ID
     * @param description         子任务描述
     * @param future              子任务输出，运行结束时完成（失败/停止以文本完成，不抛异常）
     */
    public record ChildHandle(Long childConversationId, Long eventId, String description,
                              CompletableFuture<String> future) {
    }

    private final Map<Long, List<ChildHandle>> childrenByParent = new ConcurrentHashMap<>();

    public void register(Long parentConversationId, ChildHandle handle) {
        childrenByParent.computeIfAbsent(parentConversationId, k -> new CopyOnWriteArrayList<>()).add(handle);
    }

    public List<ChildHandle> childrenOf(Long parentConversationId) {
        return childrenByParent.getOrDefault(parentConversationId, List.of());
    }

    public void clear(Long parentConversationId) {
        childrenByParent.remove(parentConversationId);
    }
}
