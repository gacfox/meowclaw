package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.AgentConfigDto;
import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.service.AgentConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/agent-configs")
public class AgentConfigController {
    private final AgentConfigService agentService;
    private final ObjectMapper objectMapper;

    public AgentConfigController(AgentConfigService agentService, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<AgentConfigDto>> list() {
        return ApiResponse.success(agentService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentConfigDto> getById(@PathVariable Long id) {
        return ApiResponse.success(agentService.findById(id));
    }

    @PostMapping
    public ApiResponse<AgentConfigDto> create(
            @RequestParam("data") String dataJson,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar) throws Exception {
        AgentConfigDto dto = objectMapper.readValue(dataJson, AgentConfigDto.class);
        return ApiResponse.success(agentService.create(dto, avatar));
    }

    @PutMapping("/{id}")
    public ApiResponse<AgentConfigDto> update(
            @PathVariable Long id,
            @RequestParam("data") String dataJson,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar) throws Exception {
        AgentConfigDto dto = objectMapper.readValue(dataJson, AgentConfigDto.class);
        return ApiResponse.success(agentService.update(id, dto, avatar));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return ApiResponse.success();
    }
}
