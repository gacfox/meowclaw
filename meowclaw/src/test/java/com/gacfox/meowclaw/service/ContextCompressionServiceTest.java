package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.interceptor.llm.LlmLoggingInterceptor;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ContextRecapRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import com.gacfox.meowclaw.service.SystemPromptService.PromptContext;
import com.gacfox.meowclaw.util.JsonUtil;
import com.gacfox.proarc.agentic.model.openai.Message;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextCompressionServiceTest {
    @Test
    void buildMessagesReplaysTheExactPromptContextSnapshot() {
        ChatEventBatchRepository batchRepository = mock(ChatEventBatchRepository.class);
        ContextRecapRepository recapRepository = mock(ContextRecapRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        JsonUtil jsonUtil = new JsonUtil(new ObjectMapper());
        ContextCompressionService service = new ContextCompressionService(
                batchRepository, recapRepository, messageRepository,
                mock(ChatPersistenceService.class), mock(LlmCallLogService.class), mock(TokenUsageService.class),
                mock(LlmLoggingInterceptor.class), mock(ChatAttachmentService.class), mock(HttpClient.class),
                new ObjectMapper(), jsonUtil);
        PromptContext promptContext = new PromptContext("<runtime_context>fixed</runtime_context>", List.of(3L));
        ChatEventBatch batch = new ChatEventBatch();
        batch.setId(1L);
        batch.setType("USER");
        batch.setStatus("COMPLETED");
        batch.setCreatedAt(1L);
        batch.setUserContent("原始输入");
        batch.setPromptContextJson(jsonUtil.dump(promptContext));
        when(batchRepository.findByConversationIdOrderByCreatedAtAsc(9L)).thenReturn(List.of(batch));
        when(recapRepository.findByConversationIdOrderByCreatedAtAsc(9L)).thenReturn(List.of());
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(9L)).thenReturn(List.of());

        List<Message> messages = service.buildMessages(9L);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(Message.ROLE_USER);
        assertThat(messages.get(0).getContent()).isEqualTo(
                "<runtime_context>fixed</runtime_context>\n\n<user_request>\n原始输入\n</user_request>");
    }
}
