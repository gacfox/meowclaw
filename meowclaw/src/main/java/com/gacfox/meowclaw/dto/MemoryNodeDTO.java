package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemoryNodeDTO {
    private Long id;
    private String type;
    private String content;
    private Long lastAccessedAt;
    private Long createdAt;
    private Long updatedAt;
    private List<MemoryEntityDTO> entities;
    private List<MemoryRelationDTO> relations;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryEntityDTO {
        private Long id;
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryRelationDTO {
        private Long id;
        private Long entityId;
        private String description;
    }
}
