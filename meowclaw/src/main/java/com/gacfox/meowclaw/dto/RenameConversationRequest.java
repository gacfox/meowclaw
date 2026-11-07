package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "重命名会话请求")
public class RenameConversationRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100")
    @Schema(description = "会话标题")
    private String title;
}
