package com.gacfox.meowclaw.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "聊天过程事件")
public class ChatEventDTO {
    @Schema(description = "事件 ID")
    private Long id;
    @Schema(description = "聊天事件类型")
    private String type;
    @Schema(description = "事件内容")
    private String content;
    @Schema(description = "工具名称")
    private String toolName;
    @Schema(description = "工具调用 ID")
    private String toolCallId;
    @Schema(description = "工具调用参数 JSON")
    private String toolArguments;
    @Schema(description = "上下文提示词 Token 数")
    private Long contextPromptTokens;
    @Schema(description = "上下文窗口长度")
    private Integer contextLength;
}
