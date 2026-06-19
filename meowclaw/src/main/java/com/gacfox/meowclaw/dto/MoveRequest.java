package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MoveRequest {
    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    @NotNull(message = "源路径不能为空")
    private String fromPath;

    @NotNull(message = "目标路径不能为空")
    private String toPath;
}
