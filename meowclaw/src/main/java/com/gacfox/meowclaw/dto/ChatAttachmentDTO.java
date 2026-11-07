package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "聊天附件")
public class ChatAttachmentDTO {
    @Schema(description = "附件名称")
    private String name;
    @Schema(description = "附件访问地址")
    private String url;
    @Schema(description = "MIME 类型")
    private String mimeType;
}
