package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTaskExecutionDTO {
    private Long id;
    private Long scheduledTaskId;
    private Long conversationId;
    private String status;
    private Long executedAt;
}
