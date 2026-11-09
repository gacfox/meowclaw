package com.gacfox.meowclaw.tool;

import com.gacfox.meowclaw.service.EnvironmentVariableService;
import com.gacfox.proarc.agentic.agent.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShellCommandTimeoutTest {
    private static final Pattern CHILD_PID = Pattern.compile("child=(\\d+)");

    @TempDir
    Path workspace;

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellTimeoutReturnsPartialOutput() {
        PowerShellTool tool = new PowerShellTool(new ShellCommandExecutor(mock(EnvironmentVariableService.class)));

        String result = tool.powershell(new ShellCommandParam(
                "Write-Output 'PARTIAL_OUTPUT_BEFORE_SLEEP'; Start-Sleep -Seconds 10", 1), context());

        assertThat(result).contains("PARTIAL_OUTPUT_BEFORE_SLEEP", "command timed out after 1 seconds");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellTimeoutTerminatesChildProcess() {
        PowerShellTool tool = new PowerShellTool(new ShellCommandExecutor(mock(EnvironmentVariableService.class)));
        String command = "$child = Start-Process powershell.exe -ArgumentList "
                + "'-NoProfile','-Command','Start-Sleep -Seconds 60' -PassThru; "
                + "Write-Output \"child=$($child.Id)\"; Wait-Process -Id $child.Id";

        String result = tool.powershell(new ShellCommandParam(command, 1), context());

        long childPid = childPid(result);
        assertThat(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void bashTimeoutTerminatesBackgroundChildAndReturnsOutput() {
        BashTool tool = new BashTool(new ShellCommandExecutor(mock(EnvironmentVariableService.class)));

        String result = tool.bash(new ShellCommandParam(
                "echo PARTIAL_OUTPUT_BEFORE_SLEEP; sleep 60 & echo \"child=$!\"; wait", 1), context());

        assertThat(result).contains("PARTIAL_OUTPUT_BEFORE_SLEEP", "command timed out after 1 seconds");
        long childPid = childPid(result);
        assertThat(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void bashTimeoutAllowsSigtermHandlerToRun() throws Exception {
        Path trapLog = workspace.resolve("trap.log");
        BashTool tool = new BashTool(new ShellCommandExecutor(mock(EnvironmentVariableService.class)));
        String command = "trap 'echo TERM >> " + trapLog + "' TERM; "
                + "echo ready; while true; do sleep 1; done";

        String result = tool.bash(new ShellCommandParam(command, 1), context());

        assertThat(result).contains("command timed out after 1 seconds");
        assertThat(Files.exists(trapLog)).isTrue();
        assertThat(Files.readString(trapLog)).contains("TERM");
    }

    private long childPid(String output) {
        Matcher matcher = CHILD_PID.matcher(output);
        assertThat(matcher.find()).as("child pid in output: %s", output).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private AgentContext context() {
        AgentContext ctx = mock(AgentContext.class);
        when(ctx.getVariables()).thenReturn(Map.of("cwd", workspace.toString()));
        return ctx;
    }
}
