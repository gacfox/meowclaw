package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.EnvironmentVariableConverter;
import com.gacfox.meowclaw.dto.CreateEnvironmentVariableRequest;
import com.gacfox.meowclaw.dto.EnvironmentVariableDTO;
import com.gacfox.meowclaw.dto.UpdateEnvironmentVariableRequest;
import com.gacfox.meowclaw.entity.EnvironmentVariable;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.EnvironmentVariableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class EnvironmentVariableService {
    private static final Pattern ENVIRONMENT_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final EnvironmentVariableRepository environmentRepository;
    private final AgentRepository agentRepository;
    private final EnvironmentVariableConverter environmentConverter;

    @Autowired
    public EnvironmentVariableService(EnvironmentVariableRepository environmentRepository,
                                      AgentRepository agentRepository,
                                      EnvironmentVariableConverter environmentConverter) {
        this.environmentRepository = environmentRepository;
        this.agentRepository = agentRepository;
        this.environmentConverter = environmentConverter;
    }

    @Transactional(readOnly = true)
    public List<EnvironmentVariableDTO> list(Long agentId) {
        if (agentId == null) {
            return environmentRepository.findByAgentIdIsNullOrderByKeyNameAsc().stream()
                    .map(environmentConverter::toDTO).toList();
        }
        requireAgent(agentId);
        List<EnvironmentVariable> variables = new ArrayList<>(
                environmentRepository.findByAgentIdIsNullOrderByKeyNameAsc());
        variables.addAll(environmentRepository.findByAgentIdOrderByKeyNameAsc(agentId));
        return variables.stream().map(environmentConverter::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, String> getMergedEnvironment(Long agentId) {
        Map<String, String> result = new LinkedHashMap<>();
        environmentRepository.findByAgentIdIsNullOrderByKeyNameAsc()
                .forEach(variable -> result.put(variable.getKeyName(), variable.getKeyValue()));
        if (agentId != null) {
            environmentRepository.findByAgentIdOrderByKeyNameAsc(agentId)
                    .forEach(variable -> result.put(variable.getKeyName(), variable.getKeyValue()));
        }
        return result;
    }

    @Transactional
    public EnvironmentVariableDTO create(CreateEnvironmentVariableRequest req) {
        if (req.getAgentId() != null) requireAgent(req.getAgentId());
        String key = normalizeKey(req.getKey());
        requireValue(req.getValue());
        requireUnique(req.getAgentId(), key, null);

        EnvironmentVariable variable = new EnvironmentVariable();
        variable.setAgentId(req.getAgentId());
        variable.setKeyName(key);
        variable.setKeyValue(req.getValue());
        long now = System.currentTimeMillis();
        variable.setCreatedAt(now);
        variable.setUpdatedAt(now);
        return environmentConverter.toDTO(environmentRepository.save(variable));
    }

    @Transactional
    public EnvironmentVariableDTO update(Long id, UpdateEnvironmentVariableRequest req) {
        EnvironmentVariable variable = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("环境变量不存在"));
        String key = normalizeKey(req.getKey());
        requireValue(req.getValue());
        requireUnique(variable.getAgentId(), key, id);
        variable.setKeyName(key);
        variable.setKeyValue(req.getValue());
        variable.setUpdatedAt(System.currentTimeMillis());
        return environmentConverter.toDTO(environmentRepository.save(variable));
    }

    @Transactional
    public void delete(Long id) {
        EnvironmentVariable variable = environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("环境变量不存在"));
        environmentRepository.delete(variable);
    }

    private void requireAgent(Long agentId) {
        if (!agentRepository.existsById(agentId)) {
            throw new IllegalArgumentException("智能体不存在");
        }
    }

    private String normalizeKey(String key) {
        String normalized = key == null ? "" : key.trim();
        if (!ENVIRONMENT_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("环境变量名无效：" + normalized);
        }
        return normalized;
    }

    private void requireValue(String value) {
        if (value == null) throw new IllegalArgumentException("环境变量值不能为null");
    }

    private void requireUnique(Long agentId, String key, Long excludedId) {
        Optional<EnvironmentVariable> existing = agentId == null
                ? environmentRepository.findByAgentIdIsNullAndKeyNameIgnoreCase(key)
                : environmentRepository.findByAgentIdAndKeyNameIgnoreCase(agentId, key);
        if (existing.isPresent() && !existing.get().getId().equals(excludedId)) {
            throw new IllegalArgumentException("当前作用域已存在同名环境变量：" + key);
        }
    }
}
