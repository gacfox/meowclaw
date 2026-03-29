package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScheduledTaskDto {
    private Long id;
    @NotBlank(message = "任务名称不能为空")
    private String name;
    @NotNull(message = "智能体配置ID不能为空")
    private Long agentConfigId;
    @NotBlank(message = "用户提示词不能为空")
    private String userPrompt;
    @NotBlank(message = "Cron表达式不能为空")
    private String cronExpression;
    private boolean newSessionEach;
    private Long boundConversationId;
    private boolean enabled;
    private Long lastExecutedAt;
    private String agentName;
}
