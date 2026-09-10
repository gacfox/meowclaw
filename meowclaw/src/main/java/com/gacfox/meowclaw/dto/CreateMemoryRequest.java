package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateMemoryRequest {
    @NotNull(message = "智能体ID不能为空")
    private Long agentId;
    @NotBlank(message = "记忆类型不能为空")
    private String type;
    @NotBlank(message = "记忆内容不能为空")
    private String content;
}
