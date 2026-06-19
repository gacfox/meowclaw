package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScheduledTaskRequest {
    @Size(max = 255, message = "任务名称长度不能超过255")
    private String name;

    private Long agentId;

    private String userPrompt;

    @Size(max = 100, message = "Cron表达式长度不能超过100")
    private String cronExpression;

    private Boolean createNewSession;
}
