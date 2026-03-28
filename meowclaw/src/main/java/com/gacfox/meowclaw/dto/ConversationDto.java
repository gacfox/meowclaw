package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConversationDto {
    private Long id;
    @NotNull(message = "智能体配置ID不能为空")
    private Long agentConfigId;
    @NotBlank(message = "会话标题不能为空")
    private String title;
}
