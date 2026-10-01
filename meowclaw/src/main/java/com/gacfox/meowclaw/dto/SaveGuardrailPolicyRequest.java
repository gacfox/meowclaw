package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveGuardrailPolicyRequest {
    @NotBlank(message = "策略名不能为空")
    private String name;
    @NotBlank(message = "策略配置不能为空")
    private String configJson;
}
