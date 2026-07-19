package com.gacfox.meowclaw.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemoryExtractionResult {
    @JsonProperty("type")
    @AgenticToolParam(name = "type", description = "记忆类型：fact/preference/rule")
    private String type;

    @JsonProperty("content")
    @AgenticToolParam(name = "content", description = "提炼后的记忆内容")
    private String content;

    @JsonProperty("entities")
    @AgenticToolParam(name = "entities", description = "提取的实体列表")
    private List<ExtractedEntity> entities;

    @JsonProperty("relations")
    @AgenticToolParam(name = "relations", description = "内容与实体的关系描述列表")
    private List<ExtractedRelation> relations;

    public static MemoryExtractionResult plain(String type, String content) {
        MemoryExtractionResult r = new MemoryExtractionResult();
        r.setType(type);
        r.setContent(content);
        r.setEntities(List.of());
        r.setRelations(List.of());
        return r;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedEntity {

        @JsonProperty("name")
        @AgenticToolParam(name = "name", description = "实体名称")
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedRelation {

        @JsonProperty("entityName")
        @AgenticToolParam(name = "entityName", description = "关联实体名称")
        private String entityName;

        @JsonProperty("description")
        @AgenticToolParam(name = "description", description = "该记忆与实体的关系描述")
        private String description;
    }
}
