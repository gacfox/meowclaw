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
@Schema(description = "向量模型测试结果")
public class EmbeddingTestResultDTO {
    @Schema(description = "是否测试成功")
    private boolean success;
    @Schema(description = "向量维度")
    private Integer dimensions;
    @Schema(description = "错误信息")
    private String errorMessage;
}
