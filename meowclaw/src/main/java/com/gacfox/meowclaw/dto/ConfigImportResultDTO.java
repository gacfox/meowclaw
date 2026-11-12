package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Schema(description = "配置导入结果")
public class ConfigImportResultDTO {
    private int totalCount;
    private int importedCount;
    private int skippedCount;
    private int failedCount;
    private int renamedCount;
    private List<CategoryResult> categories = new ArrayList<>();
    private List<ItemResult> items = new ArrayList<>();

    @Getter
    @Setter
    public static class CategoryResult {
        private String type;
        private int totalCount;
        private int importedCount;
        private int skippedCount;
        private int failedCount;
        private int renamedCount;

        public CategoryResult(String type) {
            this.type = type;
        }
    }

    @Getter
    @Setter
    public static class ItemResult {
        private String type;
        private Long sourceId;
        private String originalName;
        private String finalName;
        private Long newId;
        private String status;
        private String message;
    }
}
