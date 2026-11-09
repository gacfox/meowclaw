package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "项目及其会话")
public class ProjectDTO {
    @Schema(description = "项目 ID")
    private Long id;
    @Schema(description = "智能体 ID")
    private Long agentId;
    @Schema(description = "项目名称")
    private String name;
    @Schema(description = "项目工作目录绝对路径")
    private String path;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
    @Schema(description = "项目下的会话列表")
    private List<ConversationDTO> conversations;
}
