package com.gacfox.meowclaw.dto;

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
public class MemoryGraphDTO {
    private List<GraphMemory> memories;
    private List<GraphEntity> entities;
    private List<GraphRelation> relations;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphMemory {
        private Long id;
        private String type;
        private String content;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEntity {
        private Long id;
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphRelation {
        private Long memoryId;
        private Long entityId;
        private String description;
    }
}
