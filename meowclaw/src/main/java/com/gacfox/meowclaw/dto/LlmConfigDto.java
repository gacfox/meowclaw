package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LlmConfigDto {
    private Long id;
    @NotBlank(message = "名称不能为空")
    private String name;
    @NotBlank(message = "API地址不能为空")
    private String apiUrl;
    @NotBlank(message = "API密钥不能为空")
    private String apiKey;
    @NotBlank(message = "模型不能为空")
    private String model;
    @NotNull(message = "上下文长度不能为空")
    private Integer maxContextLength;
    private Double temperature;
}
