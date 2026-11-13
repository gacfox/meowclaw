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
@Schema(description = "系统资源监控开关修改请求")
public class SystemResourceMonitorEnabledRequest {
    @NotNull(message = "系统资源监控开关不能为空")
    @Schema(description = "是否开启系统资源监控")
    private Boolean enabled;
}
