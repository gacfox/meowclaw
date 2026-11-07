package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件读取结果：文本以 content 返回，图片以 dataUrl（base64）返回，其余为 UNSUPPORTED。
 */
@Getter
@AllArgsConstructor
@Schema(description = "文件内容读取结果")
public class FileContentDTO {
    @Schema(description = "文件内容类型")
    public enum Kind {TEXT, IMAGE, UNSUPPORTED}

    @Schema(description = "文件内容类型")
    private final Kind kind;
    @Schema(description = "MIME 类型")
    private final String mimeType;
    @Schema(description = "文本文件内容")
    private final String content;
    @Schema(description = "图片的 Base64 数据地址")
    private final String dataUrl;
}
