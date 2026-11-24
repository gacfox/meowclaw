package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "工作区批量操作结果")
public class BatchOperationResultDTO {
    private final List<BatchOperationItemDTO> results;
}
