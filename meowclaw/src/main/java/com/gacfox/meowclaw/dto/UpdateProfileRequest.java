package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @Size(min = 3, max = 50, message = "用户名长度需在3-50之间")
    private String username;

    @Size(max = 100, message = "显示名称长度不能超过100")
    private String displayName;
}
