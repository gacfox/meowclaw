package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BashTool {
    private final ShellCommandExecutor executor;

    @Autowired
    public BashTool(ShellCommandExecutor executor) {
        this.executor = executor;
    }

    @AgenticTool(name = "bash", description = "在Bash中执行命令并返回输出，适用于Linux或macOS主机")
    public String bash(@AgenticToolParam(name = "param", description = "执行参数") ShellCommandParam param,
                       AgentContext ctx) {
        return executor.execute(param, ctx, List.of("bash", "-c"));
    }
}
