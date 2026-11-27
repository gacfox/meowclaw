package com.gacfox.meowclaw.service.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

/**
 * 子任务句柄。
 */
@Getter
@AllArgsConstructor
public class ChildHandle {
    /** 子会话ID */
    private final Long childConversationId;
    /** 父批次中subagent事件ID */
    private final Long eventId;
    /** 子任务描述 */
    private final String description;
    /** 子任务输出，运行结束时完成（失败/停止以文本完成，不抛异常） */
    private final CompletableFuture<String> future;
}
