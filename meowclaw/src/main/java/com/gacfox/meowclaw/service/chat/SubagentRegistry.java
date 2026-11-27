package com.gacfox.meowclaw.service.chat;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 子智能体运行注册表：父会话ID到其子任务句柄列表的映射，与父运行同生命周期
 */
@Component
public class SubagentRegistry {
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
