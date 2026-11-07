package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作区内一个文件/目录的元信息，path 为相对工作区根的正斜杠路径。
 */
@Getter
@AllArgsConstructor
@Schema(description = "工作区文件或目录信息")
public class FileEntryDTO {
    @Schema(description = "文件或目录名称")
    private final String name;
    @Schema(description = "工作区相对路径")
    private final String path;
    @Schema(description = "是否为目录")
    private final boolean directory;
    @Schema(description = "文件大小（字节）")
    private final long size;
    @Schema(description = "最后修改时间戳")
    private final long lastModified;
}
