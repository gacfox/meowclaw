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
public class ProjectDTO {
    private Long id;
    private Long agentId;
    private String name;
    private Long createdAt;
    private Long updatedAt;
    private List<ConversationDTO> conversations;
}
