package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgentConfigDto {
    private Long id;
    @NotBlank(message = "智能体名称不能为空")
    private String name;
    private String avatar;
    private String systemPrompt;
    private String enabledTools;
    @NotNull(message = "默认LLM不能为空")
    private Long defaultLlmId;
    private String workspaceFolder;
}
