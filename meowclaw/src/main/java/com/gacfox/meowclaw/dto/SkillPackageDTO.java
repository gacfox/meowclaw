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
@Schema(description = "技能包信息")
public class SkillPackageDTO {
    @Schema(description = "技能包 ID")
    private Long id;
    @Schema(description = "技能包名称")
    private String name;
    @Schema(description = "技能包说明")
    private String description;
    @Schema(description = "服务端存储文件名")
    private String storedFilename;
    @Schema(description = "原始文件名")
    private String originalFilename;
    @Schema(description = "文件大小（字节）")
    private Long fileSize;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
