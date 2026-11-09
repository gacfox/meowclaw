package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.ProjectConverter;
import com.gacfox.meowclaw.dto.ProjectDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.Project;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProjectServiceTest {
    @TempDir
    Path workspace;

    private ProjectRepository projectRepository;
    private ConversationRepository conversationRepository;
    private ConversationService conversationService;
    private AgentRepository agentRepository;
    private ChatEventBatchRepository chatEventBatchRepository;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        conversationService = mock(ConversationService.class);
        agentRepository = mock(AgentRepository.class);
        chatEventBatchRepository = mock(ChatEventBatchRepository.class);
        ProjectConverter converter = new ProjectConverter() {
            @Override
            public ProjectDTO toDTO(Project entity) {
                return new ProjectDTO(entity.getId(), entity.getAgentId(), entity.getName(), entity.getPath(),
                        entity.getCreatedAt(), entity.getUpdatedAt(), null);
            }
        };
        projectService = new ProjectService(projectRepository, conversationRepository, conversationService,
                converter, agentRepository, chatEventBatchRepository);
    }

    @Test
    void createRejectsDuplicateName() {
        when(projectRepository.existsByAgentIdAndName(1L, "重复")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(1L, "重复", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void createUsesProjectIdDirectoryAndCreatesIt() {
        Agent agent = agent();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            if (project.getId() == null) project.setId(7L);
            return project;
        });

        ProjectDTO result = projectService.create(1L, "项目", null);

        Path expected = workspace.resolve("projects/7").toAbsolutePath().normalize();
        assertThat(result.getPath()).isEqualTo(expected.toString());
        assertThat(Files.isDirectory(expected)).isTrue();
    }

    @Test
    void updateRejectsNameTakenByAnother() {
        Project existing = project(1L, "旧名", "old");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectRepository.existsByAgentIdAndName(1L, "被占用")).thenReturn(true);

        assertThatThrownBy(() -> projectService.update(1L, "被占用", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void updatePathCreatesDirectoryAndResetsConversationCwd() {
        Project existing = project(1L, "项目", "old");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationRepository.findByProjectId(1L)).thenReturn(List.of(conversation));

        ProjectDTO result = projectService.update(1L, null, "custom");

        Path expected = workspace.resolve("custom").toAbsolutePath().normalize();
        assertThat(result.getPath()).isEqualTo(expected.toString());
        assertThat(Files.isDirectory(expected)).isTrue();
        verify(conversationService).updateCwd(11L, expected.toString());
    }

    @Test
    void updatePathRejectsRunningConversations() {
        Project existing = project(1L, "项目", "old");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        when(chatEventBatchRepository.existsRunningByProjectId(1L)).thenReturn(true);

        assertThatThrownBy(() -> projectService.update(1L, null, "new"))
                .hasMessageContaining("运行中的会话");
    }

    @Test
    void deleteCascadesConversationsWithoutDeletingDirectory() throws Exception {
        Files.createDirectories(workspace.resolve("project"));
        Project project = project(1L, "项目", workspace.resolve("project").toString());
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        Conversation c1 = new Conversation();
        c1.setId(11L);
        Conversation c2 = new Conversation();
        c2.setId(12L);
        when(conversationRepository.findByProjectId(1L)).thenReturn(List.of(c1, c2));

        projectService.delete(1L);

        verify(conversationService).deleteBatch(List.of(11L, 12L));
        verify(projectRepository).delete(project);
        assertThat(Files.exists(workspace.resolve("project"))).isTrue();
    }

    private Agent agent() {
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setWorkspaceFolder(workspace.toString());
        return agent;
    }

    private Project project(Long id, String name, String path) {
        Project project = new Project();
        project.setId(id);
        project.setAgentId(1L);
        project.setName(name);
        project.setPath(path);
        project.setCreatedAt(0L);
        project.setUpdatedAt(0L);
        return project;
    }
}
