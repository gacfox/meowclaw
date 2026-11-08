package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class PowerShellTool {
    private final ShellCommandExecutor executor;

    @Autowired
    public PowerShellTool(ShellCommandExecutor executor) {
        this.executor = executor;
    }

    @AgenticTool(name = "powershell", description = "在Windows PowerShell中以独立脚本块执行命令并返回文本输出；发生终止错误时可能中断后续语句，适用于Windows主机")
    public String powershell(@AgenticToolParam(name = "param", description = "执行参数") ShellCommandParam param,
                             AgentContext ctx) {
        String source = Base64.getEncoder().encodeToString(
                param.getCommand().getBytes(StandardCharsets.UTF_16LE));
        String command = """
                $ProgressPreference = 'SilentlyContinue'
                try {
                    $source = [Text.Encoding]::Unicode.GetString([Convert]::FromBase64String('%s'))
                    $scriptBlock = [ScriptBlock]::Create($source)
                    & $scriptBlock *>&1 | ForEach-Object { $_ | Out-String }
                } catch {
                    $_ | Out-String
                }
                if ($null -ne $LASTEXITCODE) { exit $LASTEXITCODE }
                """.formatted(source);
        String encodedCommand = Base64.getEncoder().encodeToString(
                command.getBytes(StandardCharsets.UTF_16LE));
        return executor.execute(param, ctx, List.of("powershell.exe", "-NoProfile", "-EncodedCommand"), encodedCommand);
    }
}
