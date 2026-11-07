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
@Schema(description = "记忆详情")
public class MemoryNodeDTO {
    @Schema(description = "记忆 ID")
    private Long id;
    @Schema(description = "记忆类型")
    private String type;
    @Schema(description = "记忆内容")
    private String content;
    @Schema(description = "最近访问时间戳")
    private Long lastAccessedAt;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
    @Schema(description = "关联实体列表")
    private List<MemoryEntityDTO> entities;
    @Schema(description = "实体关系列表")
    private List<MemoryRelationDTO> relations;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "记忆关联实体")
    public static class MemoryEntityDTO {
        @Schema(description = "实体 ID")
        private Long id;
        @Schema(description = "实体名称")
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "记忆与实体的关联关系")
    public static class MemoryRelationDTO {
        @Schema(description = "关系 ID")
        private Long id;
        @Schema(description = "实体 ID")
        private Long entityId;
        @Schema(description = "关系描述")
        private String description;
    }
}
