package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.agent.ReActAgent;
import com.gacfox.meowclaw.agent.tool.Tool;
import com.gacfox.meowclaw.agent.tool.ToolRegistry;
import com.gacfox.meowclaw.dto.ChatRequestDto;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatService {
    private final ToolRegistry toolRegistry;
    private final ConversationRepository conversationRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final LlmConfigRepository llmConfigRepository;
    private final MessageRepository messageRepository;
    private final TodoService todoService;
    @Value("${agent.workspace.base-dir:./data/workspaces}")
    private String agentWorkspaceBaseDir;

    private final ObjectMapper objectMapper;

    public ChatService(ToolRegistry toolRegistry,
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
        this.objectMapper = new ObjectMapper();
    }

    public Flux<String> chatStream(ChatRequestDto request) {
        Long conversationId = request.getConversationId();
        log.info("会话[{}]处理聊天请求...", conversationId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ServiceNotSatisfiedException("会话不存在"));
        AgentConfig agentConfig = agentConfigRepository.findById(conversation.getAgentConfigId())
                .orElseThrow(() -> new ServiceNotSatisfiedException("智能体不存在"));
        LlmConfig llmConfig = llmConfigRepository.findById(agentConfig.getDefaultLlmId())
                .orElseThrow(() -> new ServiceNotSatisfiedException("LLM配置不存在"));
        List<MessageDto> history = loadConversationHistory(conversationId);
        List<Tool> tools = toolRegistry.parseAndLoadTools(agentConfig.getEnabledTools());
        log.info("AgentConfig '{}' 加载了 {} 个工具", agentConfig.getName(), tools.size());

        ReActAgent reactAgent = new ReActAgent(
                agentConfig,
                llmConfig,
                history,
                tools,
                agentWorkspaceBaseDir,
                conversationId,
                todoService
        );
        return reactAgent.chat(request.getContent())
                .map(event -> {
                    try {
                        return objectMapper.writeValueAsString(event);
                    } catch (JsonProcessingException e) {
                        log.error("JSON序列化失败", e);
                        return "{\"type\":\"error\",\"content\":\"序列化失败\"}";
                    }
                })
                .publishOn(Schedulers.boundedElastic())
                .doOnComplete(() ->
                        saveConversationHistory(conversationId, history));
    }

    private List<MessageDto> loadConversationHistory(Long conversationId) {
        try {
            List<Message> records = messageRepository.findByConversationId(conversationId);
            List<MessageDto> messages = new ArrayList<>();
            for (Message record : records) {
                MessageDto dto = new MessageDto();
                dto.setRole(record.getRole());
                dto.setContent(record.getContent());
                dto.setTimestamp(record.getCreatedAt());
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
            messageRepository.deleteByConversationId(conversationId);
            for (MessageDto msg : messagesToSave) {
                messageRepository.save(conversationId, msg.getRole(), msg.getContent(), msg.getTimestamp());
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
}
