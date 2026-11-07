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
@Schema(description = "语言模型配置")
public class LlmDTO {
    @Schema(description = "语言模型配置 ID")
    private Long id;
    @Schema(description = "配置名称")
    private String name;
    @Schema(description = "模型 API 端点地址")
    private String endpointUrl;
    @Schema(description = "API 密钥")
    private String sk;
    @Schema(description = "模型名称")
    private String model;
    @Schema(description = "最大输出 Token 数")
    private Integer maxTokens;
    @Schema(description = "上下文窗口长度")
    private Integer contextLength;
    @Schema(description = "采样温度")
    private Integer temperature;
    @Schema(description = "模型能力标签")
    private String capabilities;
    @Schema(description = "可选推理强度配置")
    private String reasoningEffortOptions;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
