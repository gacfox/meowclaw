package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "定时任务信息")
public class ScheduledTaskDTO {
    @Schema(description = "定时任务 ID")
    private Long id;
    @Schema(description = "任务名称")
    private String name;
    @Schema(description = "智能体 ID")
    private Long agentId;
    @Schema(description = "用户提示词")
    private String userPrompt;
    @Schema(description = "Cron 调度表达式")
    private String cronExpression;
    @Schema(description = "每次执行是否创建新会话")
    private Boolean createNewSession;
    @Schema(description = "是否启用")
    private Boolean enabled;
    @Schema(description = "最近一次执行状态")
    private String lastStatus;
    @Schema(description = "最近执行时间戳")
    private Long lastExecutedAt;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
