package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "追踪留存策略修改请求")
public class TraceRetentionPolicyRequest {
    @NotNull(message = "留存策略不能为空")
    @Schema(description = "留存策略")
    private TraceRetentionPolicy policy;
}
