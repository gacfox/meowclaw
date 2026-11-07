package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "保存护栏策略请求")
public class SaveGuardrailPolicyRequest {
    @NotBlank(message = "策略名不能为空")
    @Schema(description = "策略名称")
    private String name;
    @NotBlank(message = "策略配置不能为空")
    @Schema(description = "护栏策略配置 JSON")
    private String configJson;
}
