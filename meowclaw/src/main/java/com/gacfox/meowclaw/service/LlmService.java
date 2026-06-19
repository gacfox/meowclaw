package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.LlmConverter;
import com.gacfox.meowclaw.dto.CreateLlmRequest;
import com.gacfox.meowclaw.dto.LlmDTO;
import com.gacfox.meowclaw.dto.UpdateLlmRequest;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.repository.LlmRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LlmService {
    private final LlmRepository llmRepository;
    private final LlmConverter llmConverter;

    @Autowired
    public LlmService(LlmRepository llmRepository, LlmConverter llmConverter) {
        this.llmRepository = llmRepository;
        this.llmConverter = llmConverter;
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
        llm.setTemperature(req.getTemperature());
        llm.setCapabilities(req.getCapabilities());
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
        if (req.getTemperature() != null) llm.setTemperature(req.getTemperature());
        if (req.getCapabilities() != null) llm.setCapabilities(req.getCapabilities());
        llm.setUpdatedAt(System.currentTimeMillis());
        return llmConverter.toDTO(llmRepository.save(llm));
    }

    @Transactional
    public void delete(Long id) {
        Llm llm = llmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        llmRepository.delete(llm);
    }
}
