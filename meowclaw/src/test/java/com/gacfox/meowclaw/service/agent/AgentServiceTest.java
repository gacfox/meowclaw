package com.gacfox.meowclaw.service.agent;

import com.gacfox.meowclaw.converter.AgentConverter;
import com.gacfox.meowclaw.dto.agent.AgentDTO;
import com.gacfox.meowclaw.dto.agent.CreateAgentRequest;
import com.gacfox.meowclaw.dto.agent.UpdateAgentRequest;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.AgentSubagentRelationRepository;
import com.gacfox.meowclaw.repository.EnvironmentVariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentServiceTest {
    private AgentRepository agentRepository;
    private EnvironmentVariableRepository environmentVariableRepository;
    private AgentSubagentRelationRepository agentSubagentRelationRepository;
    private AgentService service;

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        environmentVariableRepository = mock(EnvironmentVariableRepository.class);
        agentSubagentRelationRepository = mock(AgentSubagentRelationRepository.class);
        AgentConverter agentConverter = mock(AgentConverter.class);
        when(agentConverter.toDTO(any(Agent.class))).thenAnswer(invocation -> {
            Agent agent = invocation.getArgument(0);
            AgentDTO dto = new AgentDTO();
            dto.setId(agent.getId());
            dto.setAllowAsSubagent(agent.getAllowAsSubagent());
            return dto;
        });
        service = new AgentService(agentRepository, agentConverter, environmentVariableRepository,
                agentSubagentRelationRepository);
        when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> {
            Agent agent = invocation.getArgument(0);
            if (agent.getId() == null) agent.setId(1L);
            return agent;
        });
    }

    @Test
    void createStoresDefaultWorkspaceAsNormalizedAbsolutePath() {
        String workspaceDir = "./target/test-workspace-" + UUID.randomUUID();
        ReflectionTestUtils.setField(service, "workspaceDir", workspaceDir);

        service.create(new CreateAgentRequest());

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository, atLeastOnce()).save(captor.capture());
        Path saved = Path.of(captor.getValue().getWorkspaceFolder());
        assertThat(saved.isAbsolute()).isTrue();
        assertThat(saved).isEqualTo(Paths.get(workspaceDir, "1").toAbsolutePath().normalize());
    }

    @Test
    void updateStoresCustomWorkspaceAsNormalizedAbsolutePath() {
        Agent agent = new Agent();
        agent.setId(1L);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setWorkspaceFolder("relative/../custom-workspace");

        service.update(1L, request);

        Path saved = Path.of(agent.getWorkspaceFolder());
        assertThat(saved.isAbsolute()).isTrue();
        assertThat(saved).isEqualTo(Paths.get("custom-workspace").toAbsolutePath().normalize());
    }

    @Test
    void deleteAlsoDeletesAgentEnvironmentVariables() {
        Agent agent = new Agent();
        agent.setId(1L);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

        service.delete(1L);

        verify(environmentVariableRepository).deleteByAgentId(1L);
        verify(agentRepository).delete(agent);
    }
}
