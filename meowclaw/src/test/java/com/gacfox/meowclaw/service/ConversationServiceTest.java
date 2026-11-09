package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.converter.*;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.Project;
import com.gacfox.meowclaw.repository.*;
import com.gacfox.meowclaw.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationServiceTest {
    private ConversationRepository conversationRepository;
    private ChatEventBatchRepository batchRepository;
    private AgentRepository agentRepository;
    private ProjectRepository projectRepository;
    private ConversationService service;
    private Map<Long, Conversation> conversations;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        batchRepository = mock(ChatEventBatchRepository.class);
        agentRepository = mock(AgentRepository.class);
        projectRepository = mock(ProjectRepository.class);
        conversations = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        ConversationConverter conversationConverter = mock(ConversationConverter.class);
        when(conversationConverter.toDTO(any(Conversation.class)))
                .thenAnswer(invocation -> new com.gacfox.meowclaw.dto.ConversationDTO());
        service = new ConversationService(conversationRepository, mock(MessageRepository.class), batchRepository,
                mock(ChatEventRepository.class), mock(ContextRecapRepository.class), agentRepository,
                conversationConverter, mock(ConversationHistoryConverter.class),
                mock(ChatEventBatchConverter.class), mock(ChatEventConverter.class),
                mock(GuardrailPolicyService.class), projectRepository, mock(ProjectConverter.class),
                objectMapper, new JsonUtil(objectMapper));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            if (conversation.getId() == null) conversation.setId(10L);
            conversations.put(conversation.getId(), conversation);
            return conversation;
        });
        when(conversationRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(conversations.get(invocation.getArgument(0))));
    }

    @Test
    void createInProjectInitializesCwd() {
        Project project = project(3L, "/workspace/agent/projects/3");
        when(projectRepository.findById(3L)).thenReturn(Optional.of(project));

        service.create(1L, "CHAT", 3L);

        assertThat(conversations.get(10L).getContextJson())
                .contains("/workspace/agent/projects/3");
    }

    @Test
    void moveInAndOutResetCwd() {
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setAgentId(1L);
        conversations.put(10L, conversation);
        Project project = project(3L, "/workspace/agent/projects/3");
        when(projectRepository.findById(3L)).thenReturn(Optional.of(project));
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setWorkspaceFolder("/workspace/agent");
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

        service.moveToProject(10L, 3L);
        assertThat(conversation.getProjectId()).isEqualTo(3L);
        assertThat(conversation.getContextJson()).contains("/workspace/agent/projects/3");

        service.moveToProject(10L, null);
        assertThat(conversation.getProjectId()).isNull();
        assertThat(conversation.getContextJson()).contains("/workspace/agent");
    }

    private Project project(Long id, String path) {
        Project project = new Project();
        project.setId(id);
        project.setAgentId(1L);
        project.setPath(path);
        return project;
    }
}
