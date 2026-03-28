package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatRequestDto {
    @NotNull(message = "会话ID不能为空")
    private Long conversationId;
    @NotBlank(message = "消息内容不能为空")
    private String content;
}
