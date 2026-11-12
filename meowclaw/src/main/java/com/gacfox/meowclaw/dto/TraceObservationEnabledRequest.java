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
@Schema(description = "追踪观测开关修改请求")
public class TraceObservationEnabledRequest {
    @NotNull(message = "追踪观测开关不能为空")
    @Schema(description = "是否开启追踪观测")
    private Boolean enabled;
}
