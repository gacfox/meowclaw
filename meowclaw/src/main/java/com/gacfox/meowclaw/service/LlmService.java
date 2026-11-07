package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.converter.LlmConverter;
import com.gacfox.meowclaw.dto.CreateLlmRequest;
import com.gacfox.meowclaw.dto.LlmDTO;
import com.gacfox.meowclaw.dto.UpdateLlmRequest;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.repository.LlmRepository;
import com.gacfox.meowclaw.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class LlmService {
    private final LlmRepository llmRepository;
    private final LlmConverter llmConverter;
    private final ObjectMapper objectMapper;
    private final JsonUtil jsonUtil;

    @Autowired
    public LlmService(LlmRepository llmRepository, LlmConverter llmConverter,
                      ObjectMapper objectMapper, JsonUtil jsonUtil) {
        this.llmRepository = llmRepository;
        this.llmConverter = llmConverter;
        this.objectMapper = objectMapper;
        this.jsonUtil = jsonUtil;
    }

    @Transactional(readOnly = true)
    public List<LlmDTO> list() {
        return llmRepository.findAll().stream().map(llmConverter::toDTO).toList();
    }

    @Transactional
    public LlmDTO create(CreateLlmRequest req) {
        Llm llm = new Llm();
        llm.setName(req.getName());
        llm.setEndpointUrl(req.getEndpointUrl());
        llm.setSk(req.getSk());
        llm.setModel(req.getModel());
        llm.setMaxTokens(req.getMaxTokens());
        llm.setContextLength(req.getContextLength());
        llm.setTemperature(req.getTemperature());
        llm.setCapabilities(req.getCapabilities());
        llm.setReasoningEffortOptions(normalizeReasoningEffortOptions(req.getReasoningEffortOptions()));
        long now = System.currentTimeMillis();
        llm.setCreatedAt(now);
        llm.setUpdatedAt(now);
        return llmConverter.toDTO(llmRepository.save(llm));
    }

    @Transactional
    public LlmDTO update(Long id, UpdateLlmRequest req) {
        Llm llm = llmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        if (req.getName() != null) llm.setName(req.getName());
        if (req.getEndpointUrl() != null) llm.setEndpointUrl(req.getEndpointUrl());
        if (req.getSk() != null) llm.setSk(req.getSk());
        if (req.getModel() != null) llm.setModel(req.getModel());
        if (req.getMaxTokens() != null) llm.setMaxTokens(req.getMaxTokens());
        if (req.getContextLength() != null) llm.setContextLength(req.getContextLength());
        if (req.getTemperature() != null) llm.setTemperature(req.getTemperature());
        if (req.getCapabilities() != null) llm.setCapabilities(req.getCapabilities());
        if (req.getReasoningEffortOptions() != null) {
            llm.setReasoningEffortOptions(normalizeReasoningEffortOptions(req.getReasoningEffortOptions()));
        }
        llm.setUpdatedAt(System.currentTimeMillis());
        return llmConverter.toDTO(llmRepository.save(llm));
    }

    @Transactional
    public void delete(Long id) {
        Llm llm = llmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        llmRepository.delete(llm);
    }

    /**
     * 校验并规范化思考强度可选项：必须为非空字符串组成的JSON数组，去空去重且保持原顺序
     */
    String normalizeReasoningEffortOptions(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || !node.isArray()) {
                throw new IllegalArgumentException("思考强度可选项必须是JSON字符串数组");
            }
            Set<String> options = new LinkedHashSet<>();
            for (JsonNode item : node) {
                if (!item.isTextual() || item.asText().isBlank()) {
                    throw new IllegalArgumentException("思考强度可选项必须全部是非空字符串");
                }
                options.add(item.asText().trim());
            }
            return options.isEmpty() ? null : jsonUtil.dump(options);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("思考强度可选项不是合法JSON数组");
        }
    }
}
