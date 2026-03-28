package com.gacfox.meowclaw.agent.tool;

import com.gacfox.meowclaw.entity.AgentConfig;
import lombok.Getter;

import java.nio.file.Path;

@Getter
public class ToolExecutionContext {
    private final AgentConfig agent;
    private final Path workspaceDir;
    private final Long conversationId;

    public ToolExecutionContext(AgentConfig agent, Path workspaceDir, Long conversationId) {
        this.agent = agent;
        this.workspaceDir = workspaceDir;
        this.conversationId = conversationId;
    }
}
