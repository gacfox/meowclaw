package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAgentRequest {
    @Size(max = 255, message = "智能体名称长度不能超过255")
    private String name;

    @Size(max = 500, message = "头像URL长度不能超过500")
    private String avatarUrl;

    private String persona;

    @Size(max = 2000, message = "启用工具配置长度不能超过2000")
    private String enabledTools;

    @Size(max = 2000, message = "启用MCP工具配置长度不能超过2000")
    private String enabledMcpTools;

    @NotNull(message = "请选择Agent执行LLM")
    private Long llmId;

    @NotNull(message = "请选择辅助LLM")
    private Long secondaryLlmId;

    private Long embeddingModelId;

    @Size(max = 500, message = "工作区路径长度不能超过500")
    private String workspaceFolder;
}
