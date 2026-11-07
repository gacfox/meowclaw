package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发送消息请求")
public class SendMessageRequest {
    @Schema(description = "消息内容")
    private String content;
    @Schema(description = "图片列表")
    private List<String> images;
    @Schema(description = "推理强度")
    private String reasoningEffort;
}
