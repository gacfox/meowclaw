package com.gacfox.meowclaw.dto;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String displayUsername;
    private String avatarUrl;
}
