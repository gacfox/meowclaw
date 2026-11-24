package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "工作区下载票据")
public class DownloadTicketDTO {
    private final String ticket;
    private final String filename;
}
