package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新用户资料请求")
public class UpdateProfileRequest {
    @Size(min = 3, max = 50, message = "用户名长度需在3-50之间")
    @Schema(description = "用户名")
    private String username;

    @Size(max = 100, message = "显示名称长度不能超过100")
    @Schema(description = "用户显示名称")
    private String displayName;
}
