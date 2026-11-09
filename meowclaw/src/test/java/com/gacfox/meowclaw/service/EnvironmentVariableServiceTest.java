package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.EnvironmentVariableConverter;
import com.gacfox.meowclaw.dto.CreateEnvironmentVariableRequest;
import com.gacfox.meowclaw.entity.EnvironmentVariable;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.EnvironmentVariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnvironmentVariableServiceTest {
    private EnvironmentVariableRepository environmentRepository;
    private AgentRepository agentRepository;
    private EnvironmentVariableService service;

    @BeforeEach
    void setUp() {
        environmentRepository = mock(EnvironmentVariableRepository.class);
        agentRepository = mock(AgentRepository.class);
        service = new EnvironmentVariableService(environmentRepository, agentRepository,
                mock(EnvironmentVariableConverter.class));
    }

    @Test
    void mergedEnvironmentUsesAgentValueOverGlobalValue() {
        when(environmentRepository.findByAgentIdIsNullOrderByKeyNameAsc()).thenReturn(List.of(
                variable(1L, null, "SHARED", "global"),
                variable(2L, null, "GLOBAL_ONLY", "global")));
        when(environmentRepository.findByAgentIdOrderByKeyNameAsc(3L)).thenReturn(List.of(
                variable(3L, 3L, "SHARED", "agent"),
                variable(4L, 3L, "AGENT_ONLY", "agent")));

        Map<String, String> result = service.getMergedEnvironment(3L);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                "GLOBAL_ONLY", "global",
                "AGENT_ONLY", "agent",
                "SHARED", "agent"));
    }

    @Test
    void createRejectsDuplicateOnlyWithinTheSameScope() {
        when(environmentRepository.findByAgentIdIsNullAndKeyNameIgnoreCase("path"))
                .thenReturn(Optional.of(variable(1L, null, "PATH", "global")));
        CreateEnvironmentVariableRequest global = new CreateEnvironmentVariableRequest();
        global.setKey("path");
        global.setValue("value");

        assertThatThrownBy(() -> service.create(global))
                .hasMessageContaining("当前作用域已存在同名环境变量");
    }

    private EnvironmentVariable variable(Long id, Long agentId, String key, String value) {
        EnvironmentVariable variable = new EnvironmentVariable();
        variable.setId(id);
        variable.setAgentId(agentId);
        variable.setKeyName(key);
        variable.setKeyValue(value);
        return variable;
    }
}
