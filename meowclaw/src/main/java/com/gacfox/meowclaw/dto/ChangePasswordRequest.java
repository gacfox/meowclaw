package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "修改密码请求")
public class ChangePasswordRequest {
    @NotBlank(message = "旧密码不能为空")
    @Schema(description = "旧密码")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "新密码长度需在6-100之间")
    @Schema(description = "新密码")
    private String newPassword;
}
