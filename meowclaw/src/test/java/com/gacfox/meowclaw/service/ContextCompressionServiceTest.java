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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
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
                mock(LlmLoggingInterceptor.class), mock(ChatAttachmentService.class), mock(HttpClient.class), jsonUtil);
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

    @ParameterizedTest
    @ValueSource(ints = {6, 15})
    void foldingKeepsLongPlainAssistantContent(int batchCount) {
        ChatEventBatchRepository batchRepository = mock(ChatEventBatchRepository.class);
        ContextRecapRepository recapRepository = mock(ContextRecapRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        JsonUtil jsonUtil = new JsonUtil(new ObjectMapper());
        ContextCompressionService service = new ContextCompressionService(
                batchRepository, recapRepository, messageRepository,
                mock(ChatPersistenceService.class), mock(LlmCallLogService.class), mock(TokenUsageService.class),
                mock(LlmLoggingInterceptor.class), mock(ChatAttachmentService.class), mock(HttpClient.class), jsonUtil);
        PromptContext promptContext = new PromptContext("<runtime_context>fixed</runtime_context>", List.of());
        List<ChatEventBatch> batches = new ArrayList<>();
        for (int i = 1; i <= batchCount; i++) {
            ChatEventBatch batch = new ChatEventBatch();
            batch.setId((long) i);
            batch.setType("USER");
            batch.setStatus("COMPLETED");
            batch.setCreatedAt((long) i);
            batch.setUserContent("输入" + i);
            batch.setPromptContextJson(jsonUtil.dump(promptContext));
            batches.add(batch);
        }
        when(batchRepository.findByConversationIdOrderByCreatedAtAsc(9L)).thenReturn(batches);
        when(recapRepository.findByConversationIdOrderByCreatedAtAsc(9L)).thenReturn(List.of());

        String intermediateContent = "阶段正文" + "完整".repeat(60);
        String toolContent = "工具结果" + "数据".repeat(60);
        String answerContent = "最终正文" + "完整".repeat(60);
        com.gacfox.meowclaw.entity.Message intermediate = new com.gacfox.meowclaw.entity.Message();
        intermediate.setBatchId(1L);
        intermediate.setRole("assistant");
        intermediate.setContent(intermediateContent);
        intermediate.setToolCallsJson("[{\"id\":\"call-1\",\"function\":{\"name\":\"read\",\"arguments\":\"{}\"}}]");
        com.gacfox.meowclaw.entity.Message tool = new com.gacfox.meowclaw.entity.Message();
        tool.setBatchId(1L);
        tool.setRole("tool");
        tool.setContent(toolContent);
        com.gacfox.meowclaw.entity.Message answer = new com.gacfox.meowclaw.entity.Message();
        answer.setBatchId(1L);
        answer.setRole("assistant");
        answer.setContent(answerContent);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(9L))
                .thenReturn(List.of(intermediate, tool, answer));

        List<Message> messages = service.buildMessages(9L);

        assertThat(messages).anyMatch(message -> Message.ROLE_ASSISTANT.equals(message.getRole())
                && answerContent.equals(message.getContent()));
        if (batchCount == 6) {
            assertThat(messages).anyMatch(message -> intermediateContent.equals(message.getContent()));
            assertThat(messages).anyMatch(message -> Message.ROLE_TOOL.equals(message.getRole())
                    && message.getContent() instanceof String content
                    && content.startsWith("工具结果")
                    && content.contains("[此处因上下文压缩已截断]"));
        } else {
            assertThat(messages).noneMatch(message -> intermediateContent.equals(message.getContent())
                    || Message.ROLE_TOOL.equals(message.getRole()));
        }
    }
}
