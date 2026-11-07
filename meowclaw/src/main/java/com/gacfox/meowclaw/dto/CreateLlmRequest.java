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
@Schema(description = "创建语言模型配置请求")
public class CreateLlmRequest {
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 255, message = "配置名称长度不能超过255")
    @Schema(description = "配置名称")
    private String name;

    @NotBlank(message = "端点URL不能为空")
    @Size(max = 500, message = "端点URL长度不能超过500")
    @Schema(description = "模型 API 端点地址")
    private String endpointUrl;

    @Size(max = 255, message = "密钥长度不能超过255")
    @Schema(description = "API 密钥")
    private String sk;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过100")
    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "最大输出 Token 数")
    private Integer maxTokens;

    @Schema(description = "上下文窗口长度")
    private Integer contextLength;

    @Schema(description = "采样温度")
    private Integer temperature;

    @Size(max = 255, message = "能力标签长度不能超过255")
    @Schema(description = "模型能力标签")
    private String capabilities;

    @Schema(description = "可选推理强度配置")
    private String reasoningEffortOptions;
}
