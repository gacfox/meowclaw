package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.AgentSubagentRelationRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final ConversationRepository conversationRepository;
    private final AgentRepository agentRepository;
    private final AgentSubagentRelationRepository subagentRelationRepository;
    private final SubagentRegistry subagentRegistry;
    private final ObjectProvider<ChatService> chatServiceProvider;
    private final JsonUtil jsonUtil;

    @Autowired
    public SubagentService(ConversationRepository conversationRepository,
                           AgentRepository agentRepository,
                           AgentSubagentRelationRepository subagentRelationRepository,
                           SubagentRegistry subagentRegistry,
                           ObjectProvider<ChatService> chatServiceProvider,
                           JsonUtil jsonUtil) {
        this.conversationRepository = conversationRepository;
        this.agentRepository = agentRepository;
        this.subagentRelationRepository = subagentRelationRepository;
        this.subagentRegistry = subagentRegistry;
        this.chatServiceProvider = chatServiceProvider;
        this.jsonUtil = jsonUtil;
    }

    /**
     * 创建沿用父智能体配置的general子智能体
     *
     * @return 给父agent的工具返回文本
     */
    public String spawn(Long parentConversationId, String toolCallId, String prompt, String description,
                        List<String> allowedTools) {
        Conversation parent = getConversation(parentConversationId);
        return spawn(parent, toolCallId, prompt, description, parent.getAgentId(), allowedTools, null);
    }

    /**
     * 创建使用目标智能体完整配置的专业子智能体
     *
     * @return 给父agent的工具返回文本
     */
    public String spawnProfessional(Long parentConversationId, String toolCallId, String prompt, String description,
                                    Long professionalAgentId) {
        Conversation parent = getConversation(parentConversationId);
        Agent professional = agentRepository.findById(professionalAgentId)
                .orElseThrow(() -> new IllegalArgumentException("专业子智能体不存在"));
        if (!Boolean.TRUE.equals(professional.getAllowAsSubagent())) {
            throw new IllegalArgumentException("目标智能体未允许作为子智能体: " + professional.getName());
        }
        if (!subagentRelationRepository.existsByParentAgentIdAndSubagentId(
                parent.getAgentId(), professionalAgentId)) {
            throw new IllegalArgumentException("当前智能体未添加该专业子智能体: " + professional.getName());
        }
        return spawn(parent, toolCallId, prompt, description, professionalAgentId, null, professional);
    }

    private Conversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("父会话不存在"));
    }

    private String spawn(Conversation parent, String toolCallId, String prompt, String description,
                         Long childAgentId, List<String> allowedTools, Agent professionalAgent) {
        Long parentConversationId = parent.getId();
        Conversation child = new Conversation();
        child.setAgentId(childAgentId);
        child.setTitle(description);
        child.setType(TYPE_SUBAGENT);
        child.setParentConversationId(parentConversationId);
        if (allowedTools != null) {
            try {
                child.setAllowedTools(jsonUtil.dump(allowedTools));
            } catch (Exception e) {
                throw new IllegalStateException("工具白名单序列化失败", e);
            }
        }
        long now = System.currentTimeMillis();
        child.setCreatedAt(now);
        child.setUpdatedAt(now);
        child = conversationRepository.save(child);

        ChatService chatService = chatServiceProvider.getObject();
        Long childConversationId = child.getId();
        Long eventId = chatService.createSubagentEvent(parentConversationId, toolCallId, childConversationId,
                description, professionalAgent);

        CompletableFuture<String> future = new CompletableFuture<>();
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<String> completedContent = new AtomicReference<>();
        AtomicBoolean stopped = new AtomicBoolean();
        Long finalEventId = eventId;
        chatService.chat(childConversationId, prompt, List.of())
                .subscribe(
                        event -> {
                            if ("error".equals(event.getType()) && error.get() == null) {
                                error.set(event.getContent());
                            } else if ("stopped".equals(event.getType())) {
                                stopped.set(true);
                            } else if ("completed".equals(event.getType())) {
                                completedContent.set(event.getContent());
                            }
                        },
                        e -> completeChild(chatService, parentConversationId, toolCallId, finalEventId,
                                childConversationId, description, professionalAgent, future,
                                "子任务执行失败: " + e.getMessage(), "failed"),
                        () -> {
                            String text;
                            String status;
                            if (error.get() != null) {
                                status = "failed";
                                text = "子任务执行失败: " + error.get();
                            } else {
                                String answer = completedContent.get();
                                if (stopped.get()) {
                                    status = "stopped";
                                    text = answer != null ? answer : "子任务已被手动停止";
                                } else {
                                    status = "completed";
                                    text = answer != null ? answer : "(子任务未产生输出)";
                                }
                            }
                            completeChild(chatService, parentConversationId, toolCallId, finalEventId,
                                    childConversationId, description, professionalAgent, future, text, status);
                        });
        subagentRegistry.register(parentConversationId,
                new SubagentRegistry.ChildHandle(childConversationId, eventId, description, future));
        log.info("子智能体已启动: parent={}, child={}, agentId={}, description={}",
                parentConversationId, childConversationId, childAgentId, description);
        String type = professionalAgent == null ? "子智能体" : "专业子智能体「" + professionalAgent.getName() + "」";
        return type + "已启动（子会话ID: " + childConversationId + "）。子任务并发执行中，"
                + "所有子任务完成后其结果会自动汇总提供，无需轮询或重复调用本工具。";
    }

    private void completeChild(ChatService chatService, Long parentConversationId, String toolCallId, Long eventId,
                               Long childConversationId, String description, Agent professionalAgent,
                               CompletableFuture<String> future, String text, String status) {
        chatService.settleSubagentEvent(parentConversationId, eventId, toolCallId, childConversationId,
                description, status, professionalAgent);
        future.complete(text);
    }
}
