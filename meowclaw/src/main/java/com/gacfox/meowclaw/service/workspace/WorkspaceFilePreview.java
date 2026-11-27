package com.gacfox.meowclaw.service.workspace;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.file.Path;

/**
 * 工作区文件预览元数据，文件内容由控制器按需流式输出。
 */
@Getter
@AllArgsConstructor
public class WorkspaceFilePreview {
    private final Path file;
    private final Kind kind;
    private final String mimeType;
    private final long size;

    public enum Kind {TEXT, IMAGE, PDF}
}
