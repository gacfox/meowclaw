package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.McpConfigDto;
import com.gacfox.meowclaw.entity.McpConfig;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.McpConfigRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class McpConfigService {
    private final McpConfigRepository mcpConfigRepository;

    public McpConfigService(McpConfigRepository mcpConfigRepository) {
        this.mcpConfigRepository = mcpConfigRepository;
    }

    public List<McpConfigDto> findAll() {
        return mcpConfigRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public McpConfigDto findById(Long id) {
        McpConfig mcpConfig = mcpConfigRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("MCP配置不存在"));
        return toDto(mcpConfig);
    }

    public McpConfig findByName(String name) {
        return mcpConfigRepository.findByName(name).orElse(null);
    }

    public McpConfigDto create(McpConfigDto dto) {
        if (mcpConfigRepository.findByName(dto.getName()).isPresent()) {
            throw new ServiceNotSatisfiedException("MCP配置名称已存在");
        }

        McpConfig mcpConfig = new McpConfig();
        BeanUtils.copyProperties(dto, mcpConfig);

        Instant now = Instant.now();
        mcpConfig.setCreatedAtInstant(now);
        mcpConfig.setUpdatedAtInstant(now);

        McpConfig ignored = mcpConfigRepository.save(mcpConfig);
        return toDto(mcpConfig);
    }

    public McpConfigDto update(Long id, McpConfigDto dto) {
        McpConfig existing = mcpConfigRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("MCP配置不存在"));

        McpConfig byName = mcpConfigRepository.findByName(dto.getName()).orElse(null);
        if (byName != null && !byName.getId().equals(id)) {
            throw new ServiceNotSatisfiedException("MCP配置名称已存在");
        }

        BeanUtils.copyProperties(dto, existing, "id", "createdAt");
        existing.setUpdatedAtInstant(Instant.now());

        McpConfig ignored = mcpConfigRepository.save(existing);
        return toDto(existing);
    }

    public void delete(Long id) {
        mcpConfigRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("MCP配置不存在"));
        mcpConfigRepository.deleteById(id);
    }

    private McpConfigDto toDto(McpConfig mcpConfig) {
        McpConfigDto dto = new McpConfigDto();
        BeanUtils.copyProperties(mcpConfig, dto);
        return dto;
    }
}