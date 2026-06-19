package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作区内一个文件/目录的元信息，path 为相对工作区根的正斜杠路径。
 */
@Getter
@AllArgsConstructor
public class FileEntry {
    private final String name;
    private final String path;
    private final boolean directory;
    private final long size;
    private final long lastModified;
}
