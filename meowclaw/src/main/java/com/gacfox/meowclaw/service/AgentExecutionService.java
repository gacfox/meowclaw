package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.agent.ReActAgent;
import com.gacfox.meowclaw.agent.tool.Tool;
import com.gacfox.meowclaw.agent.tool.ToolRegistry;
import com.gacfox.meowclaw.dto.ChatStreamEventDto;
import com.gacfox.meowclaw.dto.MessageDto;
import com.gacfox.meowclaw.entity.AgentConfig;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.LlmConfig;
import com.gacfox.meowclaw.entity.Message;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.AgentConfigRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.LlmConfigRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AgentExecutionService {
    private final ToolRegistry toolRegistry;
    private final ConversationRepository conversationRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final LlmConfigRepository llmConfigRepository;
    private final MessageRepository messageRepository;
    private final TodoService todoService;

    @Value("${agent.workspace.base-dir:./data/workspaces}")
    private String agentWorkspaceBaseDir;

    public AgentExecutionService(ToolRegistry toolRegistry,
                                 ConversationRepository conversationRepository,
                                 AgentConfigRepository agentConfigRepository,
                                 LlmConfigRepository llmConfigRepository,
                                 MessageRepository messageRepository,
                                 TodoService todoService) {
        this.toolRegistry = toolRegistry;
        this.conversationRepository = conversationRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.llmConfigRepository = llmConfigRepository;
        this.messageRepository = messageRepository;
        this.todoService = todoService;
    }

    public void executeAgent(Long agentConfigId, Long conversationId, String userPrompt) {
        log.info("执行智能体: agentConfigId={}, conversationId={}", agentConfigId, conversationId);

        AgentConfig agentConfig = agentConfigRepository.findById(agentConfigId)
                .orElseThrow(() -> new ServiceNotSatisfiedException("智能体配置不存在"));

        LlmConfig llmConfig = null;
        if (agentConfig.getDefaultLlmId() != null) {
            llmConfig = llmConfigRepository.findById(agentConfig.getDefaultLlmId())
                    .orElseThrow(() -> new ServiceNotSatisfiedException("LLM配置不存在"));
        }

        if (llmConfig == null) {
            log.error("智能体没有配置LLM: agentConfigId={}", agentConfigId);
            return;
        }

        List<MessageDto> history = loadConversationHistory(conversationId);

        List<Tool> tools = toolRegistry.parseAndLoadTools(agentConfig.getEnabledTools());
        List<Tool> mcpTools = toolRegistry.loadToolsWithMcp(agentConfig.getEnabledTools(), agentConfig.getEnabledMcpTools());
        tools.addAll(mcpTools);

        ReActAgent reactAgent = new ReActAgent(
                agentConfig,
                llmConfig,
                history,
                tools,
                agentWorkspaceBaseDir,
                conversationId,
                todoService
        );

        StringBuilder responseBuilder = new StringBuilder();

        reactAgent.chat(userPrompt)
                .doOnNext(event -> {
                    if ("content".equals(event.getType())) {
                        responseBuilder.append(event.getContent());
                    }
                })
                .blockLast();

        saveConversationHistory(conversationId, history);

        log.info("智能体执行完成: conversationId={}", conversationId);
    }

    private List<MessageDto> loadConversationHistory(Long conversationId) {
        List<Message> records = messageRepository.findByConversationId(conversationId);
        List<MessageDto> messages = new ArrayList<>();
        for (Message record : records) {
            MessageDto dto = new MessageDto();
            dto.setId(record.getId());
            dto.setRole(record.getRole());
            dto.setContent(record.getContent());
            dto.setTimestamp(record.getCreatedAt());
            dto.setInputTokens(record.getInputTokens());
            dto.setOutputTokens(record.getOutputTokens());
            dto.setApiUrl(record.getApiUrl());
            dto.setModel(record.getModel());
            messages.add(dto);
        }
        return messages;
    }

    private void saveConversationHistory(Long conversationId, List<MessageDto> messages) {
        List<MessageDto> messagesToSave = messages.stream()
                .filter(m -> MessageDto.ROLE_USER.equals(m.getRole())
                        || MessageDto.ROLE_ASSISTANT.equals(m.getRole())
                        || MessageDto.ROLE_TOOL.equals(m.getRole()))
                .toList();

        for (MessageDto msg : messagesToSave) {
            if (msg.getId() == null) {
                messageRepository.save(
                        conversationId,
                        msg.getRole(),
                        msg.getContent(),
                        msg.getApiUrl(),
                        msg.getModel(),
                        msg.getInputTokens(),
                        msg.getOutputTokens(),
                        msg.getTimestamp());
            }
        }

        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation != null) {
            conversation.setUpdatedAt(Instant.now().toEpochMilli());
            conversationRepository.save(conversation);
        }
    }
}
