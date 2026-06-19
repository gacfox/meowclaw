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
    /** STDIO / STREAMABLE_HTTP / SSE */
    private String protocol;
    /** 协议配置JSON字符串 */
    private String config;
    private Boolean enabled;
    /** CONNECTED / DISCONNECTED / ERROR */
    private String status;
    /** 解析 toolsCache 后的工具列表（含服务名前缀） */
    private List<McpToolDTO> tools;
    private String errorMessage;
    private Long lastCheckedAt;
    private Long createdAt;
    private Long updatedAt;
}
