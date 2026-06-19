package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.AgentConverter;
import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.dto.CreateAgentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentRequest;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
public class AgentService {
    private final AgentRepository agentRepository;
    private final AgentConverter agentConverter;

    @Value("${meowclaw.workspace-dir}")
    private String workspaceDir;

    @Value("${meowclaw.data-dir}")
    private String dataDir;

    @Autowired
    public AgentService(AgentRepository agentRepository, AgentConverter agentConverter) {
        this.agentRepository = agentRepository;
        this.agentConverter = agentConverter;
    }

    @Transactional(readOnly = true)
    public List<AgentDTO> list() {
        return agentRepository.findAll().stream().map(agentConverter::toDTO).toList();
    }

    @Transactional
    public AgentDTO create(CreateAgentRequest req) {
        Agent agent = new Agent();
        agent.setName(req.getName());
        agent.setAvatarUrl(req.getAvatarUrl());
        agent.setPersona(req.getPersona());
        agent.setEnabledTools(req.getEnabledTools());
        agent.setEnabledMcpTools(req.getEnabledMcpTools());
        agent.setLlmId(req.getLlmId());
        long now = System.currentTimeMillis();
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        agent = agentRepository.save(agent);

        if (req.getWorkspaceFolder() != null && !req.getWorkspaceFolder().isBlank()) {
            agent.setWorkspaceFolder(req.getWorkspaceFolder());
        } else {
            agent.setWorkspaceFolder(Paths.get(workspaceDir, String.valueOf(agent.getId())).toString());
        }
        agent.setUpdatedAt(System.currentTimeMillis());
        agent = agentRepository.save(agent);

        try {
            Files.createDirectories(Paths.get(agent.getWorkspaceFolder()));
        } catch (IOException ignored) {
        }

        return agentConverter.toDTO(agent);
    }

    @Transactional
    public AgentDTO update(Long id, UpdateAgentRequest req) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        if (req.getName() != null) agent.setName(req.getName());
        if (req.getAvatarUrl() != null) agent.setAvatarUrl(req.getAvatarUrl());
        if (req.getPersona() != null) agent.setPersona(req.getPersona());
        if (req.getEnabledTools() != null) agent.setEnabledTools(req.getEnabledTools());
        if (req.getEnabledMcpTools() != null) agent.setEnabledMcpTools(req.getEnabledMcpTools());
        if (req.getLlmId() != null) agent.setLlmId(req.getLlmId());
        if (req.getWorkspaceFolder() != null) agent.setWorkspaceFolder(req.getWorkspaceFolder());
        agent.setUpdatedAt(System.currentTimeMillis());
        return agentConverter.toDTO(agentRepository.save(agent));
    }

    @Transactional
    public String updateAvatar(Long id, MultipartFile file) throws IOException {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));

        deleteAvatarFile(agent.getAvatarUrl());

        Path avatarDir = Paths.get(dataDir, "upload", "avatar").toAbsolutePath();
        Files.createDirectories(avatarDir);

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String filename = "agent_" + id + "_" + System.currentTimeMillis() + ext;
        Path targetPath = avatarDir.resolve(filename);
        file.transferTo(targetPath);

        String avatarUrl = "/upload/avatar/" + filename;
        agent.setAvatarUrl(avatarUrl);
        agent.setUpdatedAt(System.currentTimeMillis());
        agentRepository.save(agent);
        return avatarUrl;
    }

    @Transactional
    public void delete(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        deleteAvatarFile(agent.getAvatarUrl());
        agentRepository.delete(agent);
    }

    private void deleteAvatarFile(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) return;
        try {
            Path file = Paths.get(dataDir, avatarUrl).toAbsolutePath();
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete avatar file: {}", avatarUrl, e);
        }
    }
}
