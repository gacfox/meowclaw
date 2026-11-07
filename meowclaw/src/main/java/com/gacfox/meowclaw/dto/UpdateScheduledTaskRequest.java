package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新定时任务请求")
public class UpdateScheduledTaskRequest {
    @Size(max = 255, message = "任务名称长度不能超过255")
    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "智能体 ID")
    private Long agentId;

    @Schema(description = "用户提示词")
    private String userPrompt;

    @Size(max = 100, message = "Cron表达式长度不能超过100")
    @Schema(description = "Cron 调度表达式")
    private String cronExpression;

    @Schema(description = "每次执行是否创建新会话")
    private Boolean createNewSession;
}
