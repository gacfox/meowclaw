package com.gacfox.meowclaw.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.AgentSubagentRelationRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubagentServiceTest {
    private ConversationRepository conversationRepository;
    private AgentRepository agentRepository;
    private AgentSubagentRelationRepository relationRepository;
    private SubagentService service;
    private Conversation parent;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        agentRepository = mock(AgentRepository.class);
        relationRepository = mock(AgentSubagentRelationRepository.class);
        ChatService chatService = mock(ChatService.class);
        ObjectProvider<ChatService> chatServiceProvider = mock(ObjectProvider.class);
        when(chatServiceProvider.getObject()).thenReturn(chatService);
        when(chatService.chat(anyLong(), anyString(), anyList())).thenReturn(Flux.empty());
        service = new SubagentService(conversationRepository, agentRepository, relationRepository,
                mock(SubagentRegistry.class), chatServiceProvider, new JsonUtil(new ObjectMapper()));
        parent = new Conversation();
        parent.setId(1L);
        parent.setAgentId(2L);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(conversationRepository.save(any())).thenAnswer(invocation -> {
            Conversation child = invocation.getArgument(0);
            child.setId(100L);
            return child;
        });
    }

    @Test
    void professionalSubagentUsesTargetAgentAndNoAllowedToolsOverride() {
        Agent professional = professionalAgent(true);
        when(agentRepository.findById(8L)).thenReturn(Optional.of(professional));
        when(relationRepository.existsByParentAgentIdAndSubagentId(2L, 8L)).thenReturn(true);

        String result = service.spawnProfessional(1L, "call-1", "审查代码", "审查", 8L);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getAgentId()).isEqualTo(8L);
        assertThat(captor.getValue().getAllowedTools()).isNull();
        assertThat(captor.getValue().getParentConversationId()).isEqualTo(1L);
        assertThat(result).contains("专业子智能体", "代码审查员");
    }

    @Test
    void generalSubagentKeepsParentAgentAndToolWhitelist() {
        service.spawn(1L, "call-1", "搜索资料", "搜索", List.of("read", "grep"));

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getAgentId()).isEqualTo(2L);
        assertThat(captor.getValue().getAllowedTools()).isEqualTo("[\"read\",\"grep\"]");
    }

    @Test
    void rejectsProfessionalSubagentWithoutAuthorization() {
        when(agentRepository.findById(8L)).thenReturn(Optional.of(professionalAgent(true)));
        when(relationRepository.existsByParentAgentIdAndSubagentId(2L, 8L)).thenReturn(false);

        assertThatThrownBy(() -> service.spawnProfessional(1L, "call-1", "任务", "任务", 8L))
                .hasMessageContaining("未添加该专业子智能体");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void rejectsProfessionalSubagentThatIsNotExposed() {
        when(agentRepository.findById(8L)).thenReturn(Optional.of(professionalAgent(false)));

        assertThatThrownBy(() -> service.spawnProfessional(1L, "call-1", "任务", "任务", 8L))
                .hasMessageContaining("未允许作为子智能体");
        verify(conversationRepository, never()).save(any());
    }

    private Agent professionalAgent(boolean allowed) {
        Agent agent = new Agent();
        agent.setId(8L);
        agent.setName("代码审查员");
        agent.setAllowAsSubagent(allowed);
        return agent;
    }
}
