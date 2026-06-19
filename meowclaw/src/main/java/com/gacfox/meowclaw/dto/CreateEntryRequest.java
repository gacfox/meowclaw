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
public class CreateEntryRequest {
    public enum Type { FILE, DIR }

    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    @NotNull(message = "路径不能为空")
    private String path;

    @NotNull(message = "类型不能为空")
    private Type type;
}
