package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ChatEventRepository;
import com.gacfox.meowclaw.repository.MessageRepository;
import com.gacfox.meowclaw.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPersistenceServiceTest {
    private ChatEventBatchRepository chatEventBatchRepository;
    private ChatPersistenceService service;

    @BeforeEach
    void setUp() {
        chatEventBatchRepository = mock(ChatEventBatchRepository.class);
        service = new ChatPersistenceService(
                chatEventBatchRepository,
                mock(ChatEventRepository.class),
                mock(MessageRepository.class),
                mock(ConversationService.class),
                mock(JsonUtil.class));
    }

    @Test
    void completeBatchPersistsAverageOutputSpeed() {
        ChatEventBatch batch = new ChatEventBatch();
        when(chatEventBatchRepository.findById(1L)).thenReturn(Optional.of(batch));

        service.completeBatch(1L, 2L, List.of(), 1200, 356, 28.6);

        assertThat(batch.getStatus()).isEqualTo("COMPLETED");
        assertThat(batch.getInputTokens()).isEqualTo(1200);
        assertThat(batch.getOutputTokens()).isEqualTo(356);
        assertThat(batch.getOutputTokensPerSecond()).isEqualTo(28.6);
        assertThat(batch.getCompletedAt()).isNotNull();
        verify(chatEventBatchRepository).save(batch);
    }
}
