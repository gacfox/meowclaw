package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.dto.ChatEventBatchDTO;
import com.gacfox.meowclaw.dto.ChatEventDTO;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.repository.ConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 子智能体编排：创建子会话、异步启动子运行、收集输出并回传父会话事件
 */
@Slf4j
@Service
public class SubagentService {
    public static final String TYPE_SUBAGENT = "SUBAGENT";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final SubagentRegistry subagentRegistry;
    private final ObjectProvider<ChatService> chatServiceProvider;

    public SubagentService(ConversationRepository conversationRepository,
                           ConversationService conversationService,
                           SubagentRegistry subagentRegistry,
                           ObjectProvider<ChatService> chatServiceProvider) {
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.subagentRegistry = subagentRegistry;
        this.chatServiceProvider = chatServiceProvider;
    }

    /**
     * 创建子会话并异步启动子智能体运行，注册子任务句柄后立即返回
     *
     * @return 给父agent的工具返回文本
     */
    public String spawn(Long parentConversationId, String toolCallId, String prompt, String description,
                        List<String> allowedTools) {
        Conversation parent = conversationRepository.findById(parentConversationId)
                .orElseThrow(() -> new IllegalArgumentException("父会话不存在"));
        Conversation child = new Conversation();
        child.setAgentId(parent.getAgentId());
        child.setTitle(description);
        child.setType(TYPE_SUBAGENT);
        child.setParentConversationId(parentConversationId);
        try {
            child.setAllowedTools(OBJECT_MAPPER.writeValueAsString(allowedTools));
        } catch (Exception e) {
            throw new IllegalStateException("工具白名单序列化失败", e);
        }
        long now = System.currentTimeMillis();
        child.setCreatedAt(now);
        child.setUpdatedAt(now);
        child = conversationRepository.save(child);

        ChatService chatService = chatServiceProvider.getObject();
        Long childConversationId = child.getId();
        Long eventId = chatService.createSubagentEvent(parentConversationId, toolCallId, childConversationId, description);

        CompletableFuture<String> future = new CompletableFuture<>();
        AtomicReference<String> error = new AtomicReference<>();
        AtomicBoolean stopped = new AtomicBoolean();
        Long finalEventId = eventId;
        chatService.chat(childConversationId, prompt, List.of())
                .subscribe(
                        event -> {
                            if ("error".equals(event.getType()) && error.get() == null) {
                                error.set(event.getContent());
                            } else if ("stopped".equals(event.getType())) {
                                stopped.set(true);
                            }
                        },
                        e -> completeChild(chatService, parentConversationId, toolCallId, finalEventId,
                                childConversationId, description, future, "子任务执行失败: " + e.getMessage(), "failed"),
                        () -> {
                            String text;
                            String status;
                            if (error.get() != null) {
                                status = "failed";
                                text = "子任务执行失败: " + error.get();
                            } else {
                                String finalAnswer = readFinalAnswer(childConversationId);
                                if (stopped.get()) {
                                    status = "stopped";
                                    text = finalAnswer != null ? finalAnswer : "子任务已被手动停止";
                                } else {
                                    status = "completed";
                                    text = finalAnswer != null ? finalAnswer : "(子任务未产生输出)";
                                }
                            }
                            completeChild(chatService, parentConversationId, toolCallId, finalEventId,
                                    childConversationId, description, future, text, status);
                        });
        subagentRegistry.register(parentConversationId,
                new SubagentRegistry.ChildHandle(childConversationId, eventId, description, future));
        log.info("子智能体已启动: parent={}, child={}, description={}", parentConversationId, childConversationId, description);
        return "子智能体已启动（子会话ID: " + childConversationId + "）。子任务并发执行中，"
                + "所有子任务完成后其结果会自动汇总提供，无需轮询或重复调用本工具。";
    }

    /**
     * 从子会话持久化的事件中读取最终回答（final_answer完整事件只落库不进事件流，不能依赖订阅收集）
     */
    private String readFinalAnswer(Long childConversationId) {
        try {
            List<ChatEventBatchDTO> batches = conversationService.listBatches(childConversationId);
            for (int i = batches.size() - 1; i >= 0; i--) {
                List<ChatEventDTO> events = batches.get(i).getEvents();
                if (events == null) {
                    continue;
                }
                for (int j = events.size() - 1; j >= 0; j--) {
                    ChatEventDTO event = events.get(j);
                    if ("final_answer".equals(event.getType()) && event.getContent() != null) {
                        return event.getContent();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取子任务输出失败: childConversationId={}", childConversationId, e);
        }
        return null;
    }

    private void completeChild(ChatService chatService, Long parentConversationId, String toolCallId, Long eventId,
                               Long childConversationId, String description, CompletableFuture<String> future,
                               String text, String status) {
        chatService.settleSubagentEvent(parentConversationId, eventId, toolCallId, childConversationId, description, status);
        future.complete(text);
    }
}
