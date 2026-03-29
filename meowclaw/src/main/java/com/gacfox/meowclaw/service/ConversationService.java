package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.ConversationDto;
import com.gacfox.meowclaw.dto.MessageDto;
import com.gacfox.meowclaw.dto.PageDto;
import com.gacfox.meowclaw.entity.AgentConfig;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.LlmConfig;
import com.gacfox.meowclaw.entity.Message;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.AgentConfigRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.LlmConfigRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final LlmConfigRepository llmConfigRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               AgentConfigRepository agentConfigRepository,
                               LlmConfigRepository llmConfigRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.llmConfigRepository = llmConfigRepository;
    }

    public PageDto<ConversationDto> findByAgentConfigId(Long agentConfigId, int page, int pageSize) {
        List<Conversation> conversations = conversationRepository.findByAgentConfigId(agentConfigId, page, pageSize);
        long total = conversationRepository.countByAgentConfigId(agentConfigId);
        List<ConversationDto> items = conversations.stream().map(this::toDto).collect(Collectors.toList());
        return PageDto.of(items, total, page, pageSize);
    }

    public PageDto<ConversationDto> findAll(int page, int pageSize) {
        List<Conversation> conversations = conversationRepository.findAll(page, pageSize);
        long total = conversationRepository.countAll();
        List<ConversationDto> items = conversations.stream().map(this::toDto).collect(Collectors.toList());
        return PageDto.of(items, total, page, pageSize);
    }

    public ConversationDto findById(Long id) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("会话不存在"));
        return toDto(conversation);
    }

    public ConversationDto create(ConversationDto dto) {
        Conversation conversation = new Conversation();
        BeanUtils.copyProperties(dto, conversation);
        if (conversation.getType() == null) {
            conversation.setType(Conversation.TYPE_CHAT);
        }

        Instant now = Instant.now();
        conversation.setCreatedAtInstant(now);
        conversation.setUpdatedAtInstant(now);

        conversationRepository.save(conversation);
        return toDto(conversation);
    }

    public Conversation createScheduledConversation(Long agentConfigId) {
        Conversation conversation = new Conversation();
        conversation.setAgentConfigId(agentConfigId);
        conversation.setTitle("定时任务会话");
        conversation.setType(Conversation.TYPE_SCHEDULED);

        Instant now = Instant.now();
        conversation.setCreatedAtInstant(now);
        conversation.setUpdatedAtInstant(now);

        conversationRepository.save(conversation);
        return conversation;
    }

    public ConversationDto update(Long id, ConversationDto dto) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("会话不存在"));

        BeanUtils.copyProperties(dto, conversation, "id", "createdAt");
        conversation.setUpdatedAtInstant(Instant.now());

        conversationRepository.save(conversation);
        return toDto(conversation);
    }

    public void delete(Long id) {
        conversationRepository.deleteById(id);
    }

    public void deleteMessagesAfter(Long conversationId, Long messageId) {
        messageRepository.deleteAfterId(conversationId, messageId);
    }

    public List<MessageDto> listMessages(Long conversationId) {
        List<Message> records = messageRepository.findByConversationId(conversationId);
        return records.stream().map(record -> {
            MessageDto dto = new MessageDto();
            dto.setId(record.getId());
            dto.setRole(record.getRole());
            dto.setContent(record.getContent());
            dto.setTimestamp(record.getCreatedAt());
            dto.setInputTokens(record.getInputTokens());
            dto.setOutputTokens(record.getOutputTokens());
            dto.setApiUrl(record.getApiUrl());
            dto.setModel(record.getModel());
            return dto;
        }).collect(Collectors.toList());
    }

    public ConversationDto generateTitle(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ServiceNotSatisfiedException("会话不存在"));

        List<Message> records = messageRepository.findByConversationId(conversationId);
        String firstAssistant = records.stream()
                .filter(m -> MessageDto.ROLE_ASSISTANT.equals(m.getRole()))
                .map(Message::getContent)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse(null);

        if (firstAssistant == null || firstAssistant.isBlank()) {
            return toDto(conversation);
        }

        String title = generateTitleByLlm(conversation, firstAssistant);
        conversation.setTitle(title);
        conversation.setUpdatedAt(Instant.now().toEpochMilli());
        conversationRepository.save(conversation);
        return toDto(conversation);
    }

    private String generateTitleByLlm(Conversation conversation, String firstAssistant) {
        try {
            LlmConfig llmConfig = getLlmConfig(conversation);
            if (llmConfig == null) {
                return toTitle(firstAssistant);
            }

            OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
                    .baseUrl(llmConfig.getApiUrl());
            if (llmConfig.getApiKey() != null && !llmConfig.getApiKey().isBlank()) {
                clientBuilder.apiKey(llmConfig.getApiKey());
            }
            OpenAIClient client = clientBuilder.build();

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(llmConfig.getModel())
                    .addSystemMessage("""
                            你是对话标题生成器。根据首条助手回复生成简短标题。只输出标题，避免引号，不超过20个字符。
                            
                            示例：
                            用户：你好！我是你的AI助手，有什么可以帮助你的吗？
                            标题：初识问候
                            
                            用户：好的，我来帮你分析这段代码的问题。首先，这个函数的时间复杂度是O(n²)，可以通过使用哈希表优化到O(n)...
                            标题：代码优化建议
                            
                            用户：今天北京的天气晴朗，气温15-25℃，空气质量良好，适合户外活动。
                            标题：北京天气查询
                            
                            用户：这是一个关于Python列表操作的问题。在Python中，你可以使用append()方法向列表末尾添加元素...
                            标题：Python列表操作
                            """)
                    .addUserMessage(firstAssistant)
                    .temperature(0.2)
                    .build();

            ChatCompletion completion = client.chat().completions().create(params);
            String title = completion.choices().get(0).message().content().orElse("");
            String normalized = title.replace("\"", "").replace("\n", "").trim();
            if (normalized.isBlank()) {
                return toTitle(firstAssistant);
            }
            if (normalized.length() > 20) {
                return normalized.substring(0, 20) + "...";
            }
            return normalized;
        } catch (Exception e) {
            log.warn("生成标题失败: {}", e.getMessage());
            return toTitle(firstAssistant);
        }
    }

    private LlmConfig getLlmConfig(Conversation conversation) {
        Optional<AgentConfig> agentOpt = agentConfigRepository.findById(conversation.getAgentConfigId());
        if (agentOpt.isEmpty()) {
            return null;
        }
        AgentConfig agent = agentOpt.get();
        if (agent.getDefaultLlmId() != null) {
            return llmConfigRepository.findById(agent.getDefaultLlmId()).orElse(null);
        }
        return null;
    }

    private String toTitle(String content) {
        String trimmed = content.trim();
        if (trimmed.length() <= 20) {
            return trimmed;
        }
        return trimmed.substring(0, 20) + "...";
    }

    private ConversationDto toDto(Conversation conversation) {
        ConversationDto dto = new ConversationDto();
        BeanUtils.copyProperties(conversation, dto);
        
        Optional<AgentConfig> agentOpt = agentConfigRepository.findById(conversation.getAgentConfigId());
        agentOpt.ifPresent(agent -> dto.setAgentName(agent.getName()));
        
        return dto;
    }
}
