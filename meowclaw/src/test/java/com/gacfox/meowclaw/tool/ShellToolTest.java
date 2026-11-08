package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShellToolTest {
    @TempDir
    Path workspace;

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellExecutesCommandInCurrentWorkspace() {
        AgentContext ctx = context();
        PowerShellTool tool = new PowerShellTool(new ShellCommandExecutor());

        String result = tool.powershell(new ShellCommandParam("Write-Output '你好'; Get-Location", 10), ctx);

        assertThat(result).contains("你好").contains(workspace.toString());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellPreservesDoubleQuotesAndVariableInterpolation() {
        PowerShellTool tool = new PowerShellTool(new ShellCommandExecutor());

        String result = tool.powershell(new ShellCommandParam(
                "$msg = \"Hello\"; Write-Host \"msg = $msg\"", 10), context());

        assertThat(result).contains("msg = Hello");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellReturnsTextWithoutCliXmlNoise() {
        PowerShellTool tool = new PowerShellTool(new ShellCommandExecutor());
        String command = "Write-Error 'bad'; Write-Warning 'warn'; Write-Output 'ok';"
                + " Write-Progress -Activity 'test' -Status 'working' -PercentComplete 50";

        String result = tool.powershell(new ShellCommandParam(command, 10), context());

        assertThat(result).doesNotContain("<CLIXML").contains("bad", "warn", "ok");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellReturnsUnknownCommandErrorAsPlainText() {
        PowerShellTool tool = new PowerShellTool(new ShellCommandExecutor());

        String result = tool.powershell(new ShellCommandParam("meowclaw-command-does-not-exist", 10), context());

        assertThat(result).doesNotContain("<CLIXML", "#< CLIXML").contains("meowclaw-command-does-not-exist");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellReturnsSyntaxErrorAsPlainText() {
        PowerShellTool tool = new PowerShellTool(new ShellCommandExecutor());

        String result = tool.powershell(new ShellCommandParam("if (", 10), context());

        assertThat(result).isNotBlank().doesNotContain("<CLIXML", "#< CLIXML");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellPreservesNativeCommandExitCode() {
        PowerShellTool tool = new PowerShellTool(new ShellCommandExecutor());

        String result = tool.powershell(new ShellCommandParam("cmd /c exit 3", 10), context());

        assertThat(result).contains("Exit code: 3").doesNotContain("<CLIXML");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void bashExecutesCommandInCurrentWorkspace() {
        AgentContext ctx = context();
        BashTool tool = new BashTool(new ShellCommandExecutor());

        String result = tool.bash(new ShellCommandParam("printf '你好\\n'; pwd", 10), ctx);

        assertThat(result).contains("你好").contains(workspace.toString());
    }

    private AgentContext context() {
        AgentContext ctx = mock(AgentContext.class);
        when(ctx.getVariables()).thenReturn(Map.of("cwd", workspace.toString()));
        return ctx;
    }
}
