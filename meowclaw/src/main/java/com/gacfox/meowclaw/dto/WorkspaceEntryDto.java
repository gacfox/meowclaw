package com.gacfox.meowclaw.dto;

import lombok.Data;

@Data
public class WorkspaceEntryDto {
    private String name;
    private String path;
    private boolean directory;
    private long size;
    private long modifiedAt;
    private String mime;
}
