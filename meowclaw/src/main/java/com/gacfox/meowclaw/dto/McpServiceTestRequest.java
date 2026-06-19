package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 测试连接请求（不持久化）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class McpServiceTestRequest {
    @NotBlank(message = "协议不能为空")
    private String protocol;

    @NotBlank(message = "协议配置不能为空")
    private String config;
}
