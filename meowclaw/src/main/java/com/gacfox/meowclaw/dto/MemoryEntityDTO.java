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
@Schema(description = "记忆实体")
public class MemoryEntityDTO {
    @Schema(description = "实体 ID")
    private Long id;
    @Schema(description = "实体名称")
    private String name;
    @Schema(description = "关联记忆数量")
    private Long memoryCount;
}
