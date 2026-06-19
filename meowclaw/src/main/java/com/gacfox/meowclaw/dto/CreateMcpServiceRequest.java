package com.gacfox.meowclaw.dto;

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
public class CreateMcpServiceRequest {
    @NotBlank(message = "服务名不能为空")
    @Size(max = 100, message = "服务名长度不能超过100")
    private String name;

    @Size(max = 500, message = "描述长度不能超过500")
    private String description;

    @NotBlank(message = "协议不能为空")
    private String protocol;

    @NotBlank(message = "协议配置不能为空")
    private String config;
}
