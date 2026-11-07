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
@Schema(description = "语言模型调用日志")
public class LlmCallLogDTO {
    @Schema(description = "调用日志 ID")
    private Long id;
    @Schema(description = "调用用途")
    private String purpose;
    @Schema(description = "模型名称")
    private String model;
    @Schema(description = "调用状态")
    private String status;
    @Schema(description = "错误信息")
    private String errorMessage;
    @Schema(description = "输入 Token 数")
    private Long inputTokens;
    @Schema(description = "输出 Token 数")
    private Long outputTokens;
    @Schema(description = "总 Token 数")
    private Long totalTokens;
    @Schema(description = "调用耗时（毫秒）")
    private Long durationMs;
    @Schema(description = "请求消息 JSON")
    private String requestMessages;
    @Schema(description = "模型响应内容")
    private String responseContent;
    @Schema(description = "响应中的工具调用 JSON")
    private String responseToolCalls;
    @Schema(description = "推理过程内容")
    private String reasoningContent;
    @Schema(description = "调用时间戳")
    private Long createdAt;
}
