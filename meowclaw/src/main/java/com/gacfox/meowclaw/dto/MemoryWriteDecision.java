package com.gacfox.meowclaw.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 记忆写入决策：辅助LLM结合相似记忆后给出的插入/更新/删除操作
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemoryWriteDecision {
    @JsonProperty("duplicate")
    @AgenticToolParam(name = "duplicate", description = "已有记忆已覆盖该内容时为true，跳过插入")
    private Boolean duplicate;

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

    @JsonProperty("updates")
    @AgenticToolParam(name = "updates", description = "需要更新内容的已有记忆列表")
    private List<MemoryUpdate> updates;

    @JsonProperty("deletes")
    @AgenticToolParam(name = "deletes", description = "需要删除的已有记忆ID列表")
    private List<Long> deletes;

    public static MemoryWriteDecision plainAdd(String type, String content) {
        MemoryWriteDecision d = new MemoryWriteDecision();
        d.setDuplicate(false);
        d.setType(type);
        d.setContent(content);
        d.setEntities(List.of());
        d.setRelations(List.of());
        d.setUpdates(List.of());
        d.setDeletes(List.of());
        return d;
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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryUpdate {

        @JsonProperty("memoryId")
        @AgenticToolParam(name = "memoryId", description = "要更新的已有记忆ID，必须来自相似记忆列表")
        private Long memoryId;

        @JsonProperty("content")
        @AgenticToolParam(name = "content", description = "修正后的完整记忆内容")
        private String content;
    }
}
