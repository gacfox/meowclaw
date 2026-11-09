package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.ProjectConverter;
import com.gacfox.meowclaw.dto.ProjectDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.Project;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ProjectConverter projectConverter;
    private final AgentRepository agentRepository;
    private final ChatEventBatchRepository chatEventBatchRepository;

    @Autowired
    public ProjectService(ProjectRepository projectRepository,
                          ConversationRepository conversationRepository,
                          ConversationService conversationService,
                          ProjectConverter projectConverter,
                          AgentRepository agentRepository,
                          ChatEventBatchRepository chatEventBatchRepository) {
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.projectConverter = projectConverter;
        this.agentRepository = agentRepository;
        this.chatEventBatchRepository = chatEventBatchRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectDTO> listByAgent(Long agentId) {
        return projectRepository.findByAgentIdOrderByCreatedAtAsc(agentId).stream()
                .map(projectConverter::toDTO).toList();
    }

    @Transactional
    public ProjectDTO create(Long agentId, String name, String requestedPath) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("项目名不能为空");
        }
        if (projectRepository.existsByAgentIdAndName(agentId, name.trim())) {
            throw new IllegalArgumentException("项目名已存在: " + name);
        }
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        Project project = new Project();
        project.setAgentId(agentId);
        project.setName(name.trim());
        long now = System.currentTimeMillis();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project = projectRepository.save(project);

        String path = resolvePath(agent, requestedPath, project.getId());
        requireUniquePath(agentId, path);
        createDirectory(path);
        project.setPath(path);
        project.setUpdatedAt(System.currentTimeMillis());
        return projectConverter.toDTO(projectRepository.save(project));
    }

    @Transactional
    public ProjectDTO update(Long id, String name, String requestedPath) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        if (name != null) {
            if (name.isBlank()) throw new IllegalArgumentException("项目名不能为空");
            if (!project.getName().equals(name.trim())
                    && projectRepository.existsByAgentIdAndName(project.getAgentId(), name.trim())) {
                throw new IllegalArgumentException("项目名已存在: " + name);
            }
            project.setName(name.trim());
        }

        String newPath = project.getPath();
        if (requestedPath != null) {
            Agent agent = agentRepository.findById(project.getAgentId())
                    .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
            newPath = resolvePath(agent, requestedPath, project.getId());
        }
        boolean pathChanged = !newPath.equals(project.getPath());
        if (pathChanged) {
            requireUniquePath(project.getAgentId(), newPath);
            if (chatEventBatchRepository.existsRunningByProjectId(id)) {
                throw new IllegalArgumentException("项目内存在运行中的会话，不能修改项目路径");
            }
            createDirectory(newPath);
            project.setPath(newPath);
        }
        project.setUpdatedAt(System.currentTimeMillis());
        project = projectRepository.save(project);
        if (pathChanged) {
            String cwd = project.getPath();
            for (Conversation conversation : conversationRepository.findByProjectId(id)) {
                conversationService.updateCwd(conversation.getId(), cwd);
            }
        }
        return projectConverter.toDTO(project);
    }

    @Transactional
    public void delete(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        List<Long> conversationIds = conversationRepository.findByProjectId(id)
                .stream().map(Conversation::getId).toList();
        conversationService.deleteBatch(conversationIds);
        projectRepository.delete(project);
    }

    private String resolvePath(Agent agent, String requestedPath, Long projectId) {
        if (agent.getWorkspaceFolder() == null || agent.getWorkspaceFolder().isBlank()) {
            throw new IllegalArgumentException("智能体未配置工作区");
        }
        Path workspace = Paths.get(agent.getWorkspaceFolder()).toAbsolutePath().normalize();
        String path = requestedPath == null || requestedPath.isBlank()
                ? "projects/" + projectId : requestedPath.trim();
        Path candidate = Paths.get(path);
        Path resolved = (candidate.isAbsolute() ? candidate : workspace.resolve(candidate))
                .toAbsolutePath().normalize();
        if (resolved.equals(workspace) || !resolved.startsWith(workspace)) {
            throw new IllegalArgumentException("项目路径必须位于智能体工作区内，且不能是工作区根目录");
        }
        return resolved.toString();
    }

    private void requireUniquePath(Long agentId, String path) {
        if (projectRepository.existsByAgentIdAndPath(agentId, path)) {
            throw new IllegalArgumentException("项目路径已被其他项目使用");
        }
    }

    private void createDirectory(String path) {
        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            throw new IllegalStateException("无法创建项目目录：" + path, e);
        }
    }
}
