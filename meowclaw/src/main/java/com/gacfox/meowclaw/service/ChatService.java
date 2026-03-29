package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.ChatRequestDto;
import com.gacfox.meowclaw.dto.ChatStreamEventDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class ChatService {
    private final ConversationExecutionService conversationExecutionService;
    private final ObjectMapper objectMapper;

    public ChatService(ConversationExecutionService conversationExecutionService) {
        this.conversationExecutionService = conversationExecutionService;
        this.objectMapper = new ObjectMapper();
    }

    public Flux<String> chatStream(ChatRequestDto request) {
        Long conversationId = request.getConversationId();
        log.info("会话[{}]处理聊天请求...", conversationId);

        Flux<ChatStreamEventDto> stream = conversationExecutionService.streamChat(conversationId, request.getContent());
        return stream.map(event -> {
            try {
                return objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                log.error("JSON序列化失败", e);
                return "{\"type\":\"error\",\"content\":\"序列化失败\"}";
            }
        });
    }
}
