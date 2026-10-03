package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.ProjectConverter;
import com.gacfox.meowclaw.dto.ProjectDTO;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.Project;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ProjectConverter projectConverter;

    @Autowired
    public ProjectService(ProjectRepository projectRepository,
                          ConversationRepository conversationRepository,
                          ConversationService conversationService,
                          ProjectConverter projectConverter) {
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.projectConverter = projectConverter;
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> listByAgent(Long agentId) {
        return projectRepository.findByAgentIdOrderByCreatedAtAsc(agentId).stream()
                .map(projectConverter::toDTO).toList();
    }

    @Transactional
    public ProjectDTO create(Long agentId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("项目名不能为空");
        }
        if (projectRepository.existsByAgentIdAndName(agentId, name.trim())) {
            throw new IllegalArgumentException("项目名已存在: " + name);
        }
        Project project = new Project();
        project.setAgentId(agentId);
        project.setName(name.trim());
        long now = System.currentTimeMillis();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        return projectConverter.toDTO(projectRepository.save(project));
    }

    @Transactional
    public ProjectDTO update(Long id, String name) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("项目名不能为空");
        }
        if (!project.getName().equals(name.trim())
                && projectRepository.existsByAgentIdAndName(project.getAgentId(), name.trim())) {
            throw new IllegalArgumentException("项目名已存在: " + name);
        }
        project.setName(name.trim());
        project.setUpdatedAt(System.currentTimeMillis());
        return projectConverter.toDTO(projectRepository.save(project));
    }

    /**
     * 删除项目并级联删除其中全部会话
     */
    @Transactional
    public void delete(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        List<Long> conversationIds = conversationRepository.findByProjectId(id)
                .stream().map(Conversation::getId).toList();
        conversationService.deleteBatch(conversationIds);
        projectRepository.delete(project);
    }
}
