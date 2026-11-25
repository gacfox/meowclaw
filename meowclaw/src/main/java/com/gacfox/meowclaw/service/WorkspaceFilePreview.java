package com.gacfox.meowclaw.service;

import java.nio.file.Path;

/**
 * 工作区文件预览元数据，文件内容由控制器按需流式输出。
 */
public record WorkspaceFilePreview(Path file, Kind kind, String mimeType, long size) {
    public enum Kind {TEXT, IMAGE, PDF}
}
