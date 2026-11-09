package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CdToolTest {
    @TempDir
    Path workspace;

    @Test
    void cdPersistsNormalizedAbsolutePath() {
        AgentContext context = mock(AgentContext.class);
        Map<String, Object> variables = new HashMap<>();
        variables.put("cwd", workspace.toString());
        when(context.getVariables()).thenReturn(variables);

        String result = new CdTool().cd(new CdParam("."), context);

        String expected = workspace.toAbsolutePath().normalize().toString();
        assertThat(result).isEqualTo("cwd=" + expected);
        assertThat(variables.get("cwd")).isEqualTo(expected);
    }
}
