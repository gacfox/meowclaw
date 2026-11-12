package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.ConfigImportResultDTO;
import com.gacfox.meowclaw.service.AgentConfigTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Tag(name = "系统配置")
@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController {
    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AgentConfigTransferService agentConfigTransferService;

    @Autowired
    public SystemConfigController(AgentConfigTransferService agentConfigTransferService) {
        this.agentConfigTransferService = agentConfigTransferService;
    }

    @Operation(summary = "导出智能体相关系统配置")
    @GetMapping("/agent-config/export")
    public ResponseEntity<byte[]> exportAgentConfig() {
        String filename = "meowclaw-agent-config-" + LocalDateTime.now().format(EXPORT_TIME_FORMAT) + ".json";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(agentConfigTransferService.exportConfig());
    }

    @Operation(summary = "导入智能体相关系统配置")
    @PostMapping("/agent-config/import")
    public ApiResult<ConfigImportResultDTO> importAgentConfig(@RequestParam("file") MultipartFile file) {
        return ApiResult.success(agentConfigTransferService.importConfig(file));
    }
}
