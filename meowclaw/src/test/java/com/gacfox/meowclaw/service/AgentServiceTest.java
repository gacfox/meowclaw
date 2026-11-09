package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.converter.AgentConverter;
import com.gacfox.meowclaw.dto.AgentEnvironmentDTO;
import com.gacfox.meowclaw.dto.CreateAgentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentEnvironmentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentRequest;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceTest {
    private AgentRepository agentRepository;
    private AgentService service;

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        service = new AgentService(agentRepository, mock(AgentConverter.class),
                new JsonUtil(new ObjectMapper()));
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
    void environmentVariablesRoundTripAndAllowEmptyValues() {
        Agent agent = new Agent();
        agent.setId(1L);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("API_MODE", "test");
        variables.put("EMPTY_VALUE", "");

        AgentEnvironmentDTO result = service.updateEnvironment(1L,
                new UpdateAgentEnvironmentRequest(variables));

        assertThat(result.getVariables()).containsExactlyEntriesOf(variables);
        assertThat(service.getEnvironmentVariables(1L)).containsExactlyEntriesOf(variables);
    }

    @Test
    void environmentVariablesRejectInvalidAndCaseInsensitiveDuplicateKeys() {
        Agent agent = new Agent();
        agent.setId(1L);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.updateEnvironment(1L,
                new UpdateAgentEnvironmentRequest(Map.of("INVALID-KEY", "value"))))
                .hasMessageContaining("环境变量名无效");
        assertThatThrownBy(() -> service.updateEnvironment(1L,
                new UpdateAgentEnvironmentRequest(Map.of("Path", "a", "PATH", "b"))))
                .hasMessageContaining("环境变量名重复");
    }
}
