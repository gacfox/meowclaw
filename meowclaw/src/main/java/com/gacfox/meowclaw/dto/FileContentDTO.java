package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件读取结果：文本以 content 返回，图片以 dataUrl（base64）返回，其余为 UNSUPPORTED。
 */
@Getter
@AllArgsConstructor
public class FileContentDTO {
    public enum Kind { TEXT, IMAGE, UNSUPPORTED }

    private final Kind kind;
    private final String mimeType;
    private final String content;
    private final String dataUrl;
}
