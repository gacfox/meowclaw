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
@Schema(description = "定时任务执行记录")
public class ScheduledTaskExecutionDTO {
    @Schema(description = "执行记录 ID")
    private Long id;
    @Schema(description = "定时任务 ID")
    private Long scheduledTaskId;
    @Schema(description = "会话 ID")
    private Long conversationId;
    @Schema(description = "执行状态")
    private String status;
    @Schema(description = "执行时间戳")
    private Long executedAt;
}
