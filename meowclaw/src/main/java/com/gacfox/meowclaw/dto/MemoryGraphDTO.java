package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 记忆-实体关系图谱
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "记忆实体关系图谱")
public class MemoryGraphDTO {
    @Schema(description = "记忆节点列表")
    private List<GraphMemory> memories;
    @Schema(description = "实体节点列表")
    private List<GraphEntity> entities;
    @Schema(description = "记忆与实体的关系列表")
    private List<GraphRelation> relations;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "图谱中的记忆节点")
    public static class GraphMemory {
        @Schema(description = "记忆 ID")
        private Long id;
        @Schema(description = "记忆类型")
        private String type;
        @Schema(description = "记忆内容")
        private String content;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "图谱中的实体节点")
    public static class GraphEntity {
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
    public static class GraphRelation {
        @Schema(description = "已有记忆 ID")
        private Long memoryId;
        @Schema(description = "实体 ID")
        private Long entityId;
        @Schema(description = "关系描述")
        private String description;
    }
}
