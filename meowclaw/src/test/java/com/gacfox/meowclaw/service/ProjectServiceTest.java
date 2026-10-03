package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.ProjectConverter;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.Project;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final ProjectService projectService = new ProjectService(
            projectRepository, conversationRepository, conversationService, new ProjectConverter() {
        @Override
        public com.gacfox.meowclaw.dto.ProjectDTO toDTO(Project entity) {
            return new com.gacfox.meowclaw.dto.ProjectDTO(entity.getId(), entity.getAgentId(), entity.getName(),
                    entity.getCreatedAt(), entity.getUpdatedAt(), null);
        }
    });

    @Test
    void createRejectsDuplicateName() {
        when(projectRepository.existsByAgentIdAndName(1L, "重复")).thenReturn(true);

        assertThatThrownBy(() -> projectService.create(1L, "重复"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void updateRejectsNameTakenByAnother() {
        Project existing = new Project(1L, 1L, "旧名", 0L, 0L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectRepository.existsByAgentIdAndName(1L, "被占用")).thenReturn(true);

        assertThatThrownBy(() -> projectService.update(1L, "被占用"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void deleteCascadesConversations() {
        Project project = new Project(1L, 1L, "项目", 0L, 0L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        Conversation c1 = new Conversation();
        c1.setId(11L);
        Conversation c2 = new Conversation();
        c2.setId(12L);
        when(conversationRepository.findByProjectId(1L)).thenReturn(List.of(c1, c2));

        projectService.delete(1L);

        verify(conversationService).deleteBatch(List.of(11L, 12L));
        verify(projectRepository).delete(project);
    }

    @Test
    void deleteEmptyProjectSkipsCascade() {
        Project project = new Project(2L, 1L, "空项目", 0L, 0L);
        when(projectRepository.findById(2L)).thenReturn(Optional.of(project));
        when(conversationRepository.findByProjectId(2L)).thenReturn(List.of());

        projectService.delete(2L);

        verify(conversationService).deleteBatch(List.of());
        verify(projectRepository).delete(project);
    }
}
