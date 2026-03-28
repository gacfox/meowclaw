package com.gacfox.meowclaw.dto;

import lombok.Data;

@Data
public class WorkspacePreviewDto {
    private String type;
    private String mime;
    private String content;
    private long size;
}
