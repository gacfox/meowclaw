package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class McpServiceDTO {
    private Long id;
    private String name;
    private String description;
    private String protocol;
    private String config;
    private Boolean enabled;
    private String status;
    private List<McpToolDTO> tools;
    private String errorMessage;
    private Long lastCheckedAt;
    private Long createdAt;
    private Long updatedAt;
}
