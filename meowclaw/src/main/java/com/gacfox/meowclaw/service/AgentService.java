package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gacfox.meowclaw.converter.AgentConverter;
import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.dto.AgentEnvironmentDTO;
import com.gacfox.meowclaw.dto.CreateAgentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentEnvironmentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentRequest;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.util.JsonUtil;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AgentService {
    private static final Pattern ENVIRONMENT_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final AgentRepository agentRepository;
    private final AgentConverter agentConverter;
    private final JsonUtil jsonUtil;

    @Value("${meowclaw.workspace-dir}")
    private String workspaceDir;

    @Value("${meowclaw.data-dir}")
    private String dataDir;

    @Autowired
    public AgentService(AgentRepository agentRepository, AgentConverter agentConverter, JsonUtil jsonUtil) {
        this.agentRepository = agentRepository;
        this.agentConverter = agentConverter;
        this.jsonUtil = jsonUtil;
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
        agent.setSecondaryLlmId(req.getSecondaryLlmId());
        agent.setEmbeddingModelId(req.getEmbeddingModelId());
        agent.setAggressiveMemoryRecall(Boolean.TRUE.equals(req.getAggressiveMemoryRecall()));
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
        if (req.getSecondaryLlmId() != null) agent.setSecondaryLlmId(req.getSecondaryLlmId());
        agent.setEmbeddingModelId(req.getEmbeddingModelId());
        if (req.getWorkspaceFolder() != null) {
            agent.setWorkspaceFolder(normalizeWorkspaceFolder(req.getWorkspaceFolder()));
        }
        if (req.getAggressiveMemoryRecall() != null) agent.setAggressiveMemoryRecall(req.getAggressiveMemoryRecall());
        agent.setUpdatedAt(System.currentTimeMillis());
        return agentConverter.toDTO(agentRepository.save(agent));
    }

    @Transactional(readOnly = true)
    public AgentEnvironmentDTO getEnvironment(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        return new AgentEnvironmentDTO(id, readEnvironmentVariables(agent));
    }

    @Transactional(readOnly = true)
    public Map<String, String> getEnvironmentVariables(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        return readEnvironmentVariables(agent);
    }

    @Transactional
    public AgentEnvironmentDTO updateEnvironment(Long id, UpdateAgentEnvironmentRequest req) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在"));
        Map<String, String> variables = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : req.getVariables().entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (!ENVIRONMENT_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("环境变量名无效：" + key);
            }
            if (variables.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(key))) {
                throw new IllegalArgumentException("环境变量名重复：" + key);
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("环境变量值不能为null：" + key);
            }
            variables.put(key, entry.getValue());
        }
        agent.setEnvironmentVariablesJson(jsonUtil.dump(variables));
        agent.setUpdatedAt(System.currentTimeMillis());
        agentRepository.save(agent);
        return new AgentEnvironmentDTO(id, variables);
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

    private Map<String, String> readEnvironmentVariables(Agent agent) {
        String json = agent.getEnvironmentVariablesJson();
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        return jsonUtil.load(json, new TypeReference<>() {
        });
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
