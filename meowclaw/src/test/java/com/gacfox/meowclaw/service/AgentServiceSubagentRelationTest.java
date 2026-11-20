package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.AgentConverter;
import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.dto.CreateAgentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentRequest;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.AgentSubagentRelation;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.AgentSubagentRelationRepository;
import com.gacfox.meowclaw.repository.EnvironmentVariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceSubagentRelationTest {
    private AgentRepository agentRepository;
    private EnvironmentVariableRepository environmentVariableRepository;
    private AgentSubagentRelationRepository relationRepository;
    private AgentService service;

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        environmentVariableRepository = mock(EnvironmentVariableRepository.class);
        relationRepository = mock(AgentSubagentRelationRepository.class);
        AgentConverter agentConverter = mock(AgentConverter.class);
        when(agentConverter.toDTO(any(Agent.class))).thenAnswer(invocation -> {
            Agent agent = invocation.getArgument(0);
            AgentDTO dto = new AgentDTO();
            dto.setId(agent.getId());
            dto.setAllowAsSubagent(agent.getAllowAsSubagent());
            return dto;
        });
        service = new AgentService(agentRepository, agentConverter, environmentVariableRepository, relationRepository);
        ReflectionTestUtils.setField(service, "workspaceDir", "./target/test-subagent-workspace-" + UUID.randomUUID());
        when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> {
            Agent agent = invocation.getArgument(0);
            if (agent.getId() == null) agent.setId(1L);
            return agent;
        });
        when(relationRepository.save(any(AgentSubagentRelation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createDefaultsAllowAsSubagentToFalseAndSavesRelations() {
        Agent subagent = agent(2L, true);
        when(agentRepository.findById(2L)).thenReturn(Optional.of(subagent));
        when(relationRepository.findByParentAgentId(1L))
                .thenReturn(List.of(), List.of(new AgentSubagentRelation(10L, 1L, 2L)));
        CreateAgentRequest request = new CreateAgentRequest();
        request.setProfessionalSubagentIds(List.of(2L));

        AgentDTO result = service.create(request);

        assertThat(result.getAllowAsSubagent()).isFalse();
        assertThat(result.getProfessionalSubagentIds()).containsExactly(2L);
        ArgumentCaptor<AgentSubagentRelation> captor = ArgumentCaptor.forClass(AgentSubagentRelation.class);
        verify(relationRepository).save(captor.capture());
        assertThat(captor.getValue().getParentAgentId()).isEqualTo(1L);
        assertThat(captor.getValue().getSubagentId()).isEqualTo(2L);
    }

    @Test
    void listPopulatesProfessionalSubagentIds() {
        Agent parent = agent(1L, false);
        when(agentRepository.findAll()).thenReturn(List.of(parent));
        when(relationRepository.findByParentAgentId(1L)).thenReturn(List.of(
                new AgentSubagentRelation(10L, 1L, 2L),
                new AgentSubagentRelation(11L, 1L, 3L)));

        List<AgentDTO> result = service.list();

        assertThat(result).singleElement().satisfies(dto ->
                assertThat(dto.getProfessionalSubagentIds()).containsExactly(2L, 3L));
    }

    @Test
    void updateWithNullRelationsDoesNotReplaceRelations() {
        Agent parent = agent(1L, true);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(relationRepository.findByParentAgentId(1L))
                .thenReturn(List.of(new AgentSubagentRelation(10L, 1L, 2L)));
        UpdateAgentRequest request = new UpdateAgentRequest();

        AgentDTO result = service.update(1L, request);

        assertThat(result.getAllowAsSubagent()).isTrue();
        assertThat(result.getProfessionalSubagentIds()).containsExactly(2L);
        verify(relationRepository, never()).deleteByParentAgentId(any());
        verify(relationRepository, never()).save(any());
    }

    @Test
    void updateWithEmptyRelationsClearsRelations() {
        Agent parent = agent(1L, false);
        AgentSubagentRelation existing = new AgentSubagentRelation(10L, 1L, 2L);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(relationRepository.findByParentAgentId(1L)).thenReturn(List.of(existing));
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setProfessionalSubagentIds(List.of());

        service.update(1L, request);

        verify(relationRepository).delete(existing);
        verify(relationRepository, never()).save(any());
    }

    @Test
    void updateRetainsExistingRelationsWithoutDeleteAndReinsert() {
        Agent parent = agent(1L, false);
        Agent subagent = agent(2L, true);
        AgentSubagentRelation existing = new AgentSubagentRelation(10L, 1L, 2L);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(agentRepository.findById(2L)).thenReturn(Optional.of(subagent));
        when(relationRepository.findByParentAgentId(1L)).thenReturn(List.of(existing));
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setProfessionalSubagentIds(List.of(2L));

        AgentDTO result = service.update(1L, request);

        assertThat(result.getProfessionalSubagentIds()).containsExactly(2L);
        verify(relationRepository, never()).delete(any());
        verify(relationRepository, never()).save(any());
    }

    @Test
    void updateOnlyDeletesRemovedAndInsertsAddedRelations() {
        Agent parent = agent(1L, false);
        Agent added = agent(3L, true);
        AgentSubagentRelation removed = new AgentSubagentRelation(10L, 1L, 2L);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(agentRepository.findById(3L)).thenReturn(Optional.of(added));
        when(relationRepository.findByParentAgentId(1L))
                .thenReturn(List.of(removed), List.of(new AgentSubagentRelation(11L, 1L, 3L)));
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setProfessionalSubagentIds(List.of(3L));

        service.update(1L, request);

        verify(relationRepository).delete(removed);
        ArgumentCaptor<AgentSubagentRelation> captor = ArgumentCaptor.forClass(AgentSubagentRelation.class);
        verify(relationRepository).save(captor.capture());
        assertThat(captor.getValue().getParentAgentId()).isEqualTo(1L);
        assertThat(captor.getValue().getSubagentId()).isEqualTo(3L);
    }

    @Test
    void updateRejectsDuplicateSubagents() {
        Agent parent = agent(1L, false);
        Agent subagent = agent(2L, true);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(agentRepository.findById(2L)).thenReturn(Optional.of(subagent));
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setProfessionalSubagentIds(List.of(2L, 2L));

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
        verify(relationRepository, never()).deleteByParentAgentId(any());
    }

    @Test
    void updateRejectsSelfAsSubagent() {
        Agent parent = agent(1L, false);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(parent));
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setProfessionalSubagentIds(List.of(1L));

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("自己");
        verify(relationRepository, never()).deleteByParentAgentId(any());
    }

    @Test
    void updateRejectsMissingSubagent() {
        Agent parent = agent(1L, false);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(agentRepository.findById(2L)).thenReturn(Optional.empty());
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setProfessionalSubagentIds(List.of(2L));

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
        verify(relationRepository, never()).deleteByParentAgentId(any());
    }

    @Test
    void updateRejectsSubagentThatDoesNotAllowUse() {
        Agent parent = agent(1L, false);
        Agent subagent = agent(2L, false);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(agentRepository.findById(2L)).thenReturn(Optional.of(subagent));
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setProfessionalSubagentIds(List.of(2L));

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未允许");
        verify(relationRepository, never()).deleteByParentAgentId(any());
    }

    @Test
    void updateCannotDisableAllowAsSubagentWhenReferenced() {
        Agent agent = agent(1L, true);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(relationRepository.findBySubagentId(1L))
                .thenReturn(List.of(new AgentSubagentRelation(10L, 2L, 1L)));
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setAllowAsSubagent(false);

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已被其他Agent引用");
        verify(agentRepository, never()).save(any());
    }

    @Test
    void deleteCannotRemoveReferencedSubagent() {
        Agent agent = agent(1L, true);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        when(relationRepository.findBySubagentId(1L))
                .thenReturn(List.of(new AgentSubagentRelation(10L, 2L, 1L)));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已被其他Agent引用");
        verify(agentRepository, never()).delete(any());
    }

    @Test
    void deleteCleansOutgoingRelations() {
        Agent agent = agent(1L, false);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

        service.delete(1L);

        verify(relationRepository).deleteByParentAgentId(1L);
        verify(agentRepository).delete(agent);
    }

    private Agent agent(Long id, boolean allowAsSubagent) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setAllowAsSubagent(allowAsSubagent);
        return agent;
    }
}
