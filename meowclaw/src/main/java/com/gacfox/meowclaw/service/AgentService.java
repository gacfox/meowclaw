package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.AgentConverter;
import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.dto.CreateAgentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentRequest;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.AgentSubagentRelation;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.AgentSubagentRelationRepository;
import com.gacfox.meowclaw.repository.EnvironmentVariableRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class AgentService {
    private final AgentRepository agentRepository;
    private final AgentConverter agentConverter;
    private final EnvironmentVariableRepository environmentVariableRepository;
    private final AgentSubagentRelationRepository agentSubagentRelationRepository;

    @Value("${meowclaw.workspace-dir}")
    private String workspaceDir;

    @Value("${meowclaw.data-dir}")
    private String dataDir;

    @Autowired
    public AgentService(AgentRepository agentRepository, AgentConverter agentConverter,
                        EnvironmentVariableRepository environmentVariableRepository,
                        AgentSubagentRelationRepository agentSubagentRelationRepository) {
        this.agentRepository = agentRepository;
        this.agentConverter = agentConverter;
        this.environmentVariableRepository = environmentVariableRepository;
        this.agentSubagentRelationRepository = agentSubagentRelationRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentDTO> list() {
        return agentRepository.findAll().stream().map(this::toDTO).toList();
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
        agent.setSecondaryLlmId(req.getSecondaryLlmId());
        agent.setEmbeddingModelId(req.getEmbeddingModelId());
        agent.setAggressiveMemoryRecall(Boolean.TRUE.equals(req.getAggressiveMemoryRecall()));
        agent.setAllowAsSubagent(Boolean.TRUE.equals(req.getAllowAsSubagent()));
        agent.setSubagentDescription(req.getSubagentDescription());
        long now = System.currentTimeMillis();
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        agent = agentRepository.save(agent);

        if (req.getWorkspaceFolder() != null && !req.getWorkspaceFolder().isBlank()) {
            agent.setWorkspaceFolder(normalizeWorkspaceFolder(req.getWorkspaceFolder()));
        } else {
            agent.setWorkspaceFolder(normalizeWorkspaceFolder(
                    Paths.get(workspaceDir, String.valueOf(agent.getId())).toString()));
        }
        agent.setUpdatedAt(System.currentTimeMillis());
        agent = agentRepository.save(agent);

        try {
            Files.createDirectories(Paths.get(agent.getWorkspaceFolder()));
        } catch (IOException ignored) {
        }

        replaceProfessionalSubagents(agent.getId(), req.getProfessionalSubagentIds());
        return toDTO(agent);
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
        if (req.getSecondaryLlmId() != null) agent.setSecondaryLlmId(req.getSecondaryLlmId());
        agent.setEmbeddingModelId(req.getEmbeddingModelId());
        if (req.getWorkspaceFolder() != null) {
            agent.setWorkspaceFolder(normalizeWorkspaceFolder(req.getWorkspaceFolder()));
        }
        if (req.getAggressiveMemoryRecall() != null) agent.setAggressiveMemoryRecall(req.getAggressiveMemoryRecall());
        if (req.getAllowAsSubagent() != null) {
            if (!req.getAllowAsSubagent()) ensureNotReferencedAsSubagent(id);
            agent.setAllowAsSubagent(req.getAllowAsSubagent());
        }
        if (req.getSubagentDescription() != null) agent.setSubagentDescription(req.getSubagentDescription());
        if (req.getProfessionalSubagentIds() != null) {
            replaceProfessionalSubagents(id, req.getProfessionalSubagentIds());
        }
        agent.setUpdatedAt(System.currentTimeMillis());
        return toDTO(agentRepository.save(agent));
    }

    /**
     * 仅更新专业子智能体关系，用于配置导入后的跨实例关联恢复
     */
    @Transactional
    public AgentDTO updateProfessionalSubagents(Long id, List<Long> professionalSubagentIds) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        replaceProfessionalSubagents(id, professionalSubagentIds);
        agent.setUpdatedAt(System.currentTimeMillis());
        return toDTO(agentRepository.save(agent));
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
        ensureNotReferencedAsSubagent(id);
        deleteAvatarFile(agent.getAvatarUrl());
        environmentVariableRepository.deleteByAgentId(id);
        agentSubagentRelationRepository.deleteByParentAgentId(id);
        agentRepository.delete(agent);
    }

    private AgentDTO toDTO(Agent agent) {
        AgentDTO dto = agentConverter.toDTO(agent);
        List<Long> professionalSubagentIds = agentSubagentRelationRepository.findByParentAgentId(agent.getId())
                .stream().map(AgentSubagentRelation::getSubagentId).toList();
        dto.setProfessionalSubagentIds(professionalSubagentIds);
        return dto;
    }

    private void replaceProfessionalSubagents(Long parentAgentId, List<Long> professionalSubagentIds) {
        List<Long> subagentIds = professionalSubagentIds == null ? List.of() : professionalSubagentIds;
        Set<Long> uniqueIds = new HashSet<>();
        for (Long subagentId : subagentIds) {
            if (subagentId == null) throw new IllegalArgumentException("专业子Agent ID不能为空");
            if (!uniqueIds.add(subagentId)) throw new IllegalArgumentException("专业子Agent不能重复");
            if (parentAgentId.equals(subagentId)) throw new IllegalArgumentException("智能体不能将自己设为专业子Agent");
            Agent subagent = agentRepository.findById(subagentId)
                    .orElseThrow(() -> new IllegalArgumentException("专业子Agent不存在: " + subagentId));
            if (!Boolean.TRUE.equals(subagent.getAllowAsSubagent())) {
                throw new IllegalArgumentException("目标智能体未允许作为专业子Agent: " + subagentId);
            }
        }

        List<AgentSubagentRelation> existingRelations =
                agentSubagentRelationRepository.findByParentAgentId(parentAgentId);
        Set<Long> existingIds = existingRelations.stream()
                .map(AgentSubagentRelation::getSubagentId)
                .collect(java.util.stream.Collectors.toSet());
        for (AgentSubagentRelation relation : existingRelations) {
            if (!uniqueIds.contains(relation.getSubagentId())) {
                agentSubagentRelationRepository.delete(relation);
            }
        }
        for (Long subagentId : subagentIds) {
            if (!existingIds.contains(subagentId)) {
                agentSubagentRelationRepository.save(new AgentSubagentRelation(null, parentAgentId, subagentId));
            }
        }
    }

    private void ensureNotReferencedAsSubagent(Long id) {
        if (!agentSubagentRelationRepository.findBySubagentId(id).isEmpty()) {
            throw new IllegalArgumentException("智能体已被其他Agent引用，不能关闭子Agent能力或删除");
        }
    }

    private String normalizeWorkspaceFolder(String folder) {
        if (folder == null || folder.isBlank()) return folder;
        return Paths.get(folder).toAbsolutePath().normalize().toString();
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
