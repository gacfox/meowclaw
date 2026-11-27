package com.gacfox.meowclaw.service.workspace;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Getter
@AllArgsConstructor
public class WorkspaceDownload {
    private final String filename;
    private final Long contentLength;
    private final StreamingResponseBody body;
}
