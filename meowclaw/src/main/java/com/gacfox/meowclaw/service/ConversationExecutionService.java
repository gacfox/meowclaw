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
import com.gacfox.meowclaw.entity.Skill;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.AgentConfigRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.LlmConfigRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ConversationExecutionService {
    private final ToolRegistry toolRegistry;
    private final ConversationRepository conversationRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final LlmConfigRepository llmConfigRepository;
    private final MessageRepository messageRepository;
    private final TodoService todoService;
    private final SkillService skillService;

    @Value("${agent.workspace.base-dir:./data/workspaces}")
    private String agentWorkspaceBaseDir;

    public ConversationExecutionService(ToolRegistry toolRegistry,
                                        ConversationRepository conversationRepository,
                                        AgentConfigRepository agentConfigRepository,
                                        LlmConfigRepository llmConfigRepository,
                                        MessageRepository messageRepository,
                                        TodoService todoService,
                                        SkillService skillService) {
        this.toolRegistry = toolRegistry;
        this.conversationRepository = conversationRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.llmConfigRepository = llmConfigRepository;
        this.messageRepository = messageRepository;
        this.todoService = todoService;
        this.skillService = skillService;
    }

    public Flux<ChatStreamEventDto> streamChat(Long conversationId, String userPrompt) {
        ExecutionContext context = loadContext(conversationId);
        ReActAgent agent = buildAgent(context);
        return agent.chat(userPrompt)
                .doOnComplete(() -> saveConversationHistory(conversationId, context.history()));
    }

    public void execute(Long conversationId, String userPrompt) {
        ExecutionContext context = loadContext(conversationId);
        ReActAgent agent = buildAgent(context);
        ChatStreamEventDto ignored = agent.chat(userPrompt).blockLast();
        saveConversationHistory(conversationId, context.history());
    }

    private ExecutionContext loadContext(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ServiceNotSatisfiedException("会话不存在"));
        AgentConfig agentConfig = agentConfigRepository.findById(conversation.getAgentConfigId())
                .orElseThrow(() -> new ServiceNotSatisfiedException("智能体不存在"));

        if (agentConfig.getDefaultLlmId() == null) {
            throw new ServiceNotSatisfiedException("LLM配置不存在");
        }
        LlmConfig llmConfig = llmConfigRepository.findById(agentConfig.getDefaultLlmId())
                .orElseThrow(() -> new ServiceNotSatisfiedException("LLM配置不存在"));

        List<MessageDto> history = loadConversationHistory(conversationId);
        List<String> enabledSkillNames = skillService.parseEnabledSkills(agentConfig.getEnabledSkills());
        List<Skill> skills = skillService.findByNames(enabledSkillNames);
        List<Tool> tools = loadTools(agentConfig, enabledSkillNames);

        return new ExecutionContext(conversationId, agentConfig, llmConfig, history, tools, skills);
    }

    private ReActAgent buildAgent(ExecutionContext context) {
        return new ReActAgent(
                context.agentConfig(),
                context.llmConfig(),
                context.history(),
                context.tools(),
                agentWorkspaceBaseDir,
                context.conversationId(),
                todoService,
                context.skills()
        );
    }

    private List<Tool> loadTools(AgentConfig agentConfig, List<String> enabledSkillNames) {
        List<Tool> tools = toolRegistry.parseAndLoadTools(agentConfig.getEnabledTools());
        List<Tool> mcpTools = toolRegistry.loadToolsWithMcp(agentConfig.getEnabledTools(), agentConfig.getEnabledMcpTools());
        tools.addAll(mcpTools);
        if (enabledSkillNames != null && !enabledSkillNames.isEmpty()) {
            boolean hasSkillTool = tools.stream().anyMatch(tool -> "skill".equals(tool.getName()));
            if (!hasSkillTool) {
                tools.addAll(toolRegistry.loadTools(List.of("skill")));
            }
        }
        log.info("AgentConfig '{}' 加载了 {} 个工具 (内置: {}, MCP: {})",
                agentConfig.getName(), tools.size(), tools.size() - mcpTools.size(), mcpTools.size());
        return tools;
    }

    private List<MessageDto> loadConversationHistory(Long conversationId) {
        try {
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
        } catch (Exception e) {
            log.warn("加载历史消息失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveConversationHistory(Long conversationId, List<MessageDto> messages) {
        try {
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
                Conversation ignored = conversationRepository.save(conversation);
            }

            log.info("保存对话历史成功，conversationId: {}, 消息数: {}", conversationId, messagesToSave.size());
        } catch (Exception e) {
            log.error("保存对话历史失败", e);
        }
    }

    private record ExecutionContext(Long conversationId,
                                    AgentConfig agentConfig,
                                    LlmConfig llmConfig,
                                    List<MessageDto> history,
                                    List<Tool> tools,
                                    List<Skill> skills) {
    }
}
