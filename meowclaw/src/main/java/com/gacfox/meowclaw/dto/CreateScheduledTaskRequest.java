package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreateScheduledTaskRequest {
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 255, message = "任务名称长度不能超过255")
    private String name;

    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    @NotBlank(message = "用户提示词不能为空")
    private String userPrompt;

    @NotBlank(message = "Cron表达式不能为空")
    @Size(max = 100, message = "Cron表达式长度不能超过100")
    private String cronExpression;

    private Boolean createNewSession;
}
