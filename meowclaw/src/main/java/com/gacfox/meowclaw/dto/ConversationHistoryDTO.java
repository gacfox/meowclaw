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
@Schema(description = "会话历史摘要")
public class ConversationHistoryDTO {
    @Schema(description = "会话 ID")
    private Long id;
    @Schema(description = "智能体 ID")
    private Long agentId;
    @Schema(description = "智能体名称")
    private String agentName;
    @Schema(description = "会话标题")
    private String title;
    @Schema(description = "会话类型")
    private String type;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
