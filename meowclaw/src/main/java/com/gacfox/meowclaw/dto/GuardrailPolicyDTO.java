package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "护栏策略配置")
public class GuardrailPolicyDTO {
    @Schema(description = "护栏策略 ID")
    private Long id;
    @Schema(description = "策略名称")
    private String name;
    @Schema(description = "护栏策略配置 JSON")
    private String configJson;
    @Schema(description = "是否为内置策略")
    private Boolean builtin;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
