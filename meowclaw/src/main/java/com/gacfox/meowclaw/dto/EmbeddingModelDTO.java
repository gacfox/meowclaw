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
@Schema(description = "向量模型配置")
public class EmbeddingModelDTO {
    @Schema(description = "向量模型配置 ID")
    private Long id;
    @Schema(description = "配置名称")
    private String name;
    @Schema(description = "模型 API 端点地址")
    private String endpointUrl;
    @Schema(description = "API 密钥")
    private String sk;
    @Schema(description = "模型名称")
    private String model;
    @Schema(description = "向量维度")
    private Integer dimensions;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
