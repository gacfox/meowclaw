package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTaskDTO {
    private Long id;
    private String name;
    private Long agentId;
    private String userPrompt;
    private String cronExpression;
    private Boolean createNewSession;
    private Boolean enabled;
    private String lastStatus;
    private Long lastExecutedAt;
    private Long createdAt;
    private Long updatedAt;
}
