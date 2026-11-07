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
@Schema(description = "更新记忆请求")
public class UpdateMemoryRequest {
    @Schema(description = "记忆类型")
    private String type;
    @Schema(description = "记忆内容")
    private String content;
}
