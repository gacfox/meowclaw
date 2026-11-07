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
@Schema(description = "侧边栏会话分组")
public class SidebarGroupsDTO {
    @Schema(description = "置顶会话列表")
    private List<ConversationDTO> pinned;
    @Schema(description = "项目分组列表")
    private List<ProjectDTO> projects;
}
