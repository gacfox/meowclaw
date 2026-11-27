package com.gacfox.meowclaw.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 图片工具结果中引用的图片文件。
 */
@Getter
@AllArgsConstructor
public class ImageRef {
    /** 图片绝对路径 */
    private final String path;
    /** 图片MIME类型 */
    private final String mimeType;
    /** 文件字节数 */
    private final long bytes;
    /** 展示给模型与界面的说明文本 */
    private final String note;
}
