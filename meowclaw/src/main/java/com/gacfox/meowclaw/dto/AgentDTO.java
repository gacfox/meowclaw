package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentDTO {
    private Long id;
    private String name;
    private String avatarUrl;
    private String persona;
    private String enabledTools;
    private String enabledMcpTools;
    private Long llmId;
    private String workspaceFolder;
    private Long createdAt;
    private Long updatedAt;
}
