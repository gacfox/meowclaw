package com.gacfox.meowclaw.service.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Base64;

/**
 * 预处理后的图片字节及MIME类型。
 */
@Getter
@AllArgsConstructor
public class ProcessedImage {
    private final byte[] bytes;
    private final String mimeType;

    public String toDataUrl() {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
