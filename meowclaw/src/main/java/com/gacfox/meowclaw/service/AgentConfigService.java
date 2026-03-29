package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.AgentConfigDto;
import com.gacfox.meowclaw.entity.AgentConfig;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.AgentConfigRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentConfigService {
    private final AgentConfigRepository agentRepository;
    private final FileStorageService fileStorageService;
    private final SkillService skillService;
    @Value("${agent.workspace.base-dir:./data/workspaces}")
    private String agentWorkspaceBaseDir;

    public AgentConfigService(AgentConfigRepository agentRepository, FileStorageService fileStorageService, SkillService skillService) {
        this.agentRepository = agentRepository;
        this.fileStorageService = fileStorageService;
        this.skillService = skillService;
    }

    public List<AgentConfigDto> findAll() {
        return agentRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public AgentConfigDto findById(Long id) {
        AgentConfig agent = agentRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("智能体不存在"));
        return toDto(agent);
    }

    public AgentConfigDto create(AgentConfigDto dto, MultipartFile avatar) {
        AgentConfig agent = new AgentConfig();
        BeanUtils.copyProperties(dto, agent);
        agent.setWorkspaceFolder(normalizeWorkspaceFolder(agent.getWorkspaceFolder()));

        if (avatar != null && !avatar.isEmpty()) {
            String avatarUrl = fileStorageService.storeFile(avatar, "avatars/agents");
            agent.setAvatar(avatarUrl);
        }

        Instant now = Instant.now();
        agent.setCreatedAtInstant(now);
        agent.setUpdatedAtInstant(now);

        AgentConfig ignored = agentRepository.save(agent);
        ensureWorkspaceDirectory(agent);
        syncAgentSkills(agent);
        return toDto(agent);
    }

    public AgentConfigDto update(Long id, AgentConfigDto dto, MultipartFile avatar) {
        AgentConfig agent = agentRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("智能体不存在"));

        BeanUtils.copyProperties(dto, agent, "id", "createdAt");
        agent.setWorkspaceFolder(normalizeWorkspaceFolder(agent.getWorkspaceFolder()));

        if (avatar != null && !avatar.isEmpty()) {
            String oldAvatarUrl = agent.getAvatar();
            String newAvatarUrl = fileStorageService.updateFile(avatar, oldAvatarUrl, "avatars/agents");
            agent.setAvatar(newAvatarUrl);
        }

        agent.setUpdatedAtInstant(Instant.now());

        AgentConfig ignored = agentRepository.save(agent);
        ensureWorkspaceDirectory(agent);
        syncAgentSkills(agent);
        return toDto(agent);
    }

    public void delete(Long id) {
        AgentConfig agent = agentRepository.findById(id)
                .orElse(null);

        if (agent != null && agent.getAvatar() != null) {
            fileStorageService.deleteFile(agent.getAvatar());
        }

        agentRepository.deleteById(id);
    }

    private AgentConfigDto toDto(AgentConfig agent) {
        AgentConfigDto dto = new AgentConfigDto();
        BeanUtils.copyProperties(agent, dto);
        return dto;
    }

    private String normalizeWorkspaceFolder(String workspaceFolder) {
        if (workspaceFolder == null) {
            return null;
        }
        String trimmed = workspaceFolder.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void ensureWorkspaceDirectory(AgentConfig agent) {
        try {
            Path workspaceDir = resolveWorkspaceDir(agent);
            Path ignored = Files.createDirectories(workspaceDir);
        } catch (Exception e) {
            // ignore
        }
    }

    private void syncAgentSkills(AgentConfig agent) {
        try {
            Path workspaceDir = resolveWorkspaceDir(agent);
            skillService.syncAgentSkills(agent, workspaceDir);
        } catch (Exception e) {
            // ignore
        }
    }

    private Path resolveWorkspaceDir(AgentConfig agent) {
        String baseDir = agentWorkspaceBaseDir == null || agentWorkspaceBaseDir.isBlank()
                ? "./data/workspaces"
                : agentWorkspaceBaseDir;
        String folder = agent.getWorkspaceFolder();
        if (folder == null || folder.isBlank()) {
            folder = agent.getName();
        }
        return Paths.get(baseDir).resolve(folder).toAbsolutePath().normalize();
    }
}
