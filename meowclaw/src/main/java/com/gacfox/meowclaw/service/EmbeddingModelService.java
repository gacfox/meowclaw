package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.EmbeddingModelConverter;
import com.gacfox.meowclaw.dto.CreateEmbeddingModelRequest;
import com.gacfox.meowclaw.dto.EmbeddingModelDTO;
import com.gacfox.meowclaw.dto.EmbeddingModelTestRequest;
import com.gacfox.meowclaw.dto.EmbeddingTestResultDTO;
import com.gacfox.meowclaw.dto.UpdateEmbeddingModelRequest;
import com.gacfox.meowclaw.entity.EmbeddingModel;
import com.gacfox.meowclaw.repository.EmbeddingModelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmbeddingModelService {
    private final EmbeddingModelRepository embeddingModelRepository;
    private final EmbeddingModelConverter embeddingModelConverter;
    private final EmbeddingService embeddingService;

    @Autowired
    public EmbeddingModelService(EmbeddingModelRepository embeddingModelRepository,
                                 EmbeddingModelConverter embeddingModelConverter,
                                 EmbeddingService embeddingService) {
        this.embeddingModelRepository = embeddingModelRepository;
        this.embeddingModelConverter = embeddingModelConverter;
        this.embeddingService = embeddingService;
    }

    @Transactional(readOnly = true)
    public List<EmbeddingModelDTO> list() {
        return embeddingModelRepository.findAll().stream()
                .map(embeddingModelConverter::toDTO).toList();
    }

    @Transactional
    public EmbeddingModelDTO create(CreateEmbeddingModelRequest req) {
        EmbeddingModel model = new EmbeddingModel();
        model.setName(req.getName());
        model.setEndpointUrl(req.getEndpointUrl());
        model.setSk(req.getSk());
        model.setModel(req.getModel());
        model.setDimensions(req.getDimensions());
        long now = System.currentTimeMillis();
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        return embeddingModelConverter.toDTO(embeddingModelRepository.save(model));
    }

    @Transactional
    public EmbeddingModelDTO update(Long id, UpdateEmbeddingModelRequest req) {
        EmbeddingModel model = embeddingModelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        model.setName(req.getName());
        model.setEndpointUrl(req.getEndpointUrl());
        model.setSk(req.getSk());
        model.setModel(req.getModel());
        model.setDimensions(req.getDimensions());
        model.setUpdatedAt(System.currentTimeMillis());
        return embeddingModelConverter.toDTO(embeddingModelRepository.save(model));
    }

    @Transactional
    public void delete(Long id) {
        EmbeddingModel model = embeddingModelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("配置不存在"));
        embeddingModelRepository.delete(model);
    }

    public EmbeddingTestResultDTO test(EmbeddingModelTestRequest req) {
        EmbeddingModel model = new EmbeddingModel();
        model.setEndpointUrl(req.getEndpointUrl());
        model.setSk(req.getSk());
        model.setModel(req.getModel());
        model.setDimensions(req.getDimensions());
        try {
            List<float[]> embeddings = embeddingService.embed(model, List.of("test"));
            if (embeddings.isEmpty()) {
                return new EmbeddingTestResultDTO(false, null, "Embedding result is empty");
            }
            return new EmbeddingTestResultDTO(true, embeddings.get(0).length, null);
        } catch (Exception e) {
            return new EmbeddingTestResultDTO(false, null, e.getMessage());
        }
    }
}
