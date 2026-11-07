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
@Schema(description = "用户信息")
public class UserDTO {
    @Schema(description = "用户 ID")
    private Long id;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "用户显示名称")
    private String displayName;
    @Schema(description = "头像地址")
    private String avatarUrl;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
