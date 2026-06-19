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
public class UpdateLlmRequest {
    @Size(max = 255, message = "配置名称长度不能超过255")
    private String name;

    @Size(max = 500, message = "端点URL长度不能超过500")
    private String endpointUrl;

    @Size(max = 255, message = "密钥长度不能超过255")
    private String sk;

    @Size(max = 100, message = "模型名称长度不能超过100")
    private String model;

    private Integer maxTokens;

    private Integer temperature;

    @Size(max = 255, message = "能力标签长度不能超过255")
    private String capabilities;
}
