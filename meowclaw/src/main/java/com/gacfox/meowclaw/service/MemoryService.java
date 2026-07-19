package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.MemoryExtractionResult;
import com.gacfox.meowclaw.dto.MemoryNodeDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.MemoryEntity;
import com.gacfox.meowclaw.entity.MemoryNode;
import com.gacfox.meowclaw.entity.MemoryNodeEntity;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.MemoryEntityRepository;
import com.gacfox.meowclaw.repository.MemoryNodeEntityRepository;
import com.gacfox.meowclaw.repository.MemoryNodeRepository;
import com.gacfox.meowclaw.util.RrfFusionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 记忆核心服务：写入、召回、获取、遗忘。
 */
@Slf4j
@Service
public class MemoryService {

    private static final Set<String> VALID_TYPES = Set.of("fact", "preference", "rule");

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    private final MemoryNodeRepository memoryNodeRepository;
    private final MemoryEntityRepository memoryEntityRepository;
    private final MemoryNodeEntityRepository memoryNodeEntityRepository;
    private final MemoryExtractionService memoryExtractionService;
    private final EmbeddingService embeddingService;
    private final MemoryLuceneWriter luceneWriter;
    private final MemoryLuceneSearcher luceneSearcher;
    private final AgentRepository agentRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public MemoryService(MemoryNodeRepository memoryNodeRepository,
                         MemoryEntityRepository memoryEntityRepository,
                         MemoryNodeEntityRepository memoryNodeEntityRepository,
                         MemoryExtractionService memoryExtractionService,
                         EmbeddingService embeddingService,
                         MemoryLuceneWriter luceneWriter,
                         MemoryLuceneSearcher luceneSearcher,
                         AgentRepository agentRepository,
                         PlatformTransactionManager transactionManager) {
        this.memoryNodeRepository = memoryNodeRepository;
        this.memoryEntityRepository = memoryEntityRepository;
        this.memoryNodeEntityRepository = memoryNodeEntityRepository;
        this.memoryExtractionService = memoryExtractionService;
        this.embeddingService = embeddingService;
        this.luceneWriter = luceneWriter;
        this.luceneSearcher = luceneSearcher;
        this.agentRepository = agentRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public MemoryNodeDTO write(Long agentId, String type, String content, Long conversationId) {
        validateType(type);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("记忆内容不能为空");
        }
        ensureAgentExists(agentId);
        lock(agentId);
        try {
            MemoryExtractionResult extraction;
            try {
                extraction = memoryExtractionService.extract(agentId, type, content, conversationId);
            } catch (Exception e) {
                log.warn("记忆结构化抽取失败，降级为普通节点: agentId={}, error={}", agentId, e.getMessage());
                extraction = MemoryExtractionResult.plain(type, content);
            }
            return writeTransactional(agentId, extraction, conversationId);
        } finally {
            unlock(agentId);
        }
    }

    private MemoryNodeDTO writeTransactional(Long agentId, MemoryExtractionResult extraction, Long conversationId) {
        return transactionTemplate.execute(status -> {
            long now = System.currentTimeMillis();

            List<String> entityNames = extraction.getEntities() == null ? List.of()
                    : extraction.getEntities().stream()
                    .map(MemoryExtractionResult.ExtractedEntity::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            List<MemoryEntity> existingEntities = entityNames.isEmpty() ? List.of()
                    : memoryEntityRepository.findByAgentIdAndNameIn(agentId, entityNames);
            Map<String, MemoryEntity> entityByName = new HashMap<>();
            for (MemoryEntity e : existingEntities) {
                entityByName.put(e.getName(), e);
            }
            List<MemoryEntity> newEntities = new ArrayList<>();
            for (String name : entityNames) {
                if (!entityByName.containsKey(name)) {
                    MemoryEntity e = new MemoryEntity();
                    e.setAgentId(agentId);
                    e.setName(name);
                    e.setCreatedAt(now);
                    e.setUpdatedAt(now);
                    newEntities.add(e);
                }
            }
            if (!newEntities.isEmpty()) {
                List<MemoryEntity> saved = memoryEntityRepository.saveAll(newEntities);
                for (MemoryEntity e : saved) {
                    entityByName.put(e.getName(), e);
                }
            }

            MemoryNode node = new MemoryNode();
            node.setAgentId(agentId);
            node.setType(extraction.getType());
            node.setContent(extraction.getContent());
            node.setCreatedAt(now);
            node.setUpdatedAt(now);
            node = memoryNodeRepository.save(node);

            List<MemoryNodeEntity> relations = new ArrayList<>();
            if (extraction.getRelations() != null) {
                for (MemoryExtractionResult.ExtractedRelation rel : extraction.getRelations()) {
                    if (rel.getEntityName() == null || rel.getDescription() == null) continue;
                    MemoryEntity entity = entityByName.get(rel.getEntityName().trim());
                    if (entity == null) {
                        MemoryEntity e = new MemoryEntity();
                        e.setAgentId(agentId);
                        e.setName(rel.getEntityName().trim());
                        e.setCreatedAt(now);
                        e.setUpdatedAt(now);
                        entity = memoryEntityRepository.save(e);
                        entityByName.put(entity.getName(), entity);
                    }
                    MemoryNodeEntity nne = new MemoryNodeEntity();
                    nne.setAgentId(agentId);
                    nne.setNodeId(node.getId());
                    nne.setEntityId(entity.getId());
                    nne.setDescription(rel.getDescription().trim());
                    nne.setCreatedAt(now);
                    nne.setUpdatedAt(now);
                    relations.add(nne);
                }
            }
            if (!relations.isEmpty()) {
                relations = memoryNodeEntityRepository.saveAll(relations);
            }

            List<float[]> vectors = computeVectors(agentId, node.getContent(), relations.stream()
                    .map(MemoryNodeEntity::getDescription)
                    .toList());
            float[] contentVector = vectors.isEmpty() ? null : vectors.get(0);
            List<MemoryLuceneWriter.MemoryNodeEntityWithVector> relationWithVectors = new ArrayList<>();
            for (int i = 0; i < relations.size(); i++) {
                float[] v = vectors.isEmpty() || i + 1 >= vectors.size() ? null : vectors.get(i + 1);
                relationWithVectors.add(new MemoryLuceneWriter.MemoryNodeEntityWithVector(relations.get(i), v));
            }

            final MemoryNode savedNode = node;
            registerAfterCommit(() -> {
                luceneWriter.addNode(agentId, savedNode, contentVector);
                luceneWriter.addRelations(agentId, relationWithVectors);
                luceneWriter.commit(agentId);
            });

            return toNodeDTO(savedNode, relations, entityByName.values());
        });
    }

    @Transactional
    public List<MemoryNodeDTO> recall(Long agentId, String query, Integer limit) {
        ensureAgentExists(agentId);
        int finalLimit = normalizeLimit(limit);
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Long embeddingModelId = getEmbeddingModelId(agentId);
        float[] queryVector = null;
        if (embeddingModelId != null) {
            List<float[]> results = embeddingService.embed(embeddingModelId, List.of(query));
            if (!results.isEmpty()) {
                queryVector = results.get(0);
            }
        }

        int pathTopK = finalLimit * 2;
        List<Long> pathA = luceneSearcher.searchNodes(agentId, query, queryVector, pathTopK);

        int relationTopK = Math.max(200, finalLimit * 10);
        int maxNodeIds = Math.min(100, finalLimit * 5);
        List<Long> relationNodeIds = luceneSearcher.searchRelationNodeIds(
                agentId, query, queryVector, relationTopK, maxNodeIds);
        List<Long> pathB = relationNodeIds.isEmpty() ? List.of()
                : luceneSearcher.searchNodesInSet(agentId, query, queryVector, new HashSet<>(relationNodeIds), pathTopK);

        List<Long> fused = RrfFusionUtil.fuse(List.of(pathA, pathB), finalLimit);
        return loadNodesWithRelations(agentId, fused, true);
    }

    @Transactional
    public List<MemoryNodeDTO> get(Long agentId, List<Long> ids) {
        ensureAgentExists(agentId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > 3) {
            throw new IllegalArgumentException("最多同时获取3条记忆");
        }
        return loadNodesWithRelations(agentId, ids, true);
    }

    public void forget(Long agentId, Long nodeId) {
        if (nodeId == null) {
            throw new IllegalArgumentException("记忆ID不能为空");
        }
        ensureAgentExists(agentId);
        lock(agentId);
        try {
            transactionTemplate.execute(status -> {
                MemoryNode node = memoryNodeRepository.findByAgentIdAndIdIn(agentId, List.of(nodeId))
                        .stream().findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("记忆不存在"));

                List<MemoryNodeEntity> relations = memoryNodeEntityRepository.findByNodeId(nodeId);
                Set<Long> entityIds = relations.stream()
                        .map(MemoryNodeEntity::getEntityId)
                        .collect(Collectors.toSet());

                memoryNodeEntityRepository.deleteByNodeId(nodeId);
                memoryNodeRepository.delete(node);

                List<Long> orphanEntityIds = new ArrayList<>();
                for (Long entityId : entityIds) {
                    if (!memoryNodeEntityRepository.existsByEntityId(entityId)) {
                        orphanEntityIds.add(entityId);
                    }
                }
                if (!orphanEntityIds.isEmpty()) {
                    memoryEntityRepository.deleteAllById(orphanEntityIds);
                }

                registerAfterCommit(() -> {
                    luceneWriter.deleteNode(agentId, nodeId);
                    luceneWriter.commit(agentId);
                });
                return null;
            });
        } finally {
            unlock(agentId);
        }
    }

    private List<MemoryNodeDTO> loadNodesWithRelations(Long agentId, List<Long> ids, boolean updateAccessTime) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<MemoryNode> nodes = memoryNodeRepository.findByAgentIdAndIdIn(agentId, ids);
        Map<Long, MemoryNode> nodeMap = new HashMap<>();
        for (MemoryNode n : nodes) {
            nodeMap.put(n.getId(), n);
        }

        List<MemoryNodeEntity> relations = memoryNodeEntityRepository.findByNodeIdIn(ids);
        Set<Long> entityIds = relations.stream()
                .map(MemoryNodeEntity::getEntityId)
                .collect(Collectors.toSet());
        List<MemoryEntity> entities = memoryEntityRepository.findAllById(entityIds);
        Map<Long, MemoryEntity> entityMap = new HashMap<>();
        for (MemoryEntity e : entities) {
            entityMap.put(e.getId(), e);
        }

        if (updateAccessTime) {
            long now = System.currentTimeMillis();
            memoryNodeRepository.updateLastAccessedIn(ids, now);
            Set<Long> relationIds = relations.stream().map(MemoryNodeEntity::getId).collect(Collectors.toSet());
            if (!relationIds.isEmpty()) {
                memoryNodeEntityRepository.updateLastAccessedIn(relationIds, now);
            }
            if (!entityIds.isEmpty()) {
                memoryEntityRepository.updateLastAccessedIn(entityIds, now);
            }
        }

        Map<Long, List<MemoryNodeEntity>> relationsByNode = new HashMap<>();
        for (MemoryNodeEntity r : relations) {
            relationsByNode.computeIfAbsent(r.getNodeId(), k -> new ArrayList<>()).add(r);
        }

        List<MemoryNodeDTO> result = new ArrayList<>();
        for (Long id : ids) {
            MemoryNode node = nodeMap.get(id);
            if (node == null) continue;
            List<MemoryNodeEntity> nodeRelations = relationsByNode.getOrDefault(id, List.of());
            result.add(toNodeDTO(node, nodeRelations, entityMap.values()));
        }
        return result;
    }

    private MemoryNodeDTO toNodeDTO(MemoryNode node, List<MemoryNodeEntity> relations, Collection<MemoryEntity> entities) {
        MemoryNodeDTO dto = new MemoryNodeDTO();
        dto.setId(node.getId());
        dto.setType(node.getType());
        dto.setContent(node.getContent());
        dto.setLastAccessedAt(node.getLastAccessedAt());
        dto.setCreatedAt(node.getCreatedAt());
        dto.setUpdatedAt(node.getUpdatedAt());

        Map<Long, MemoryEntity> entityMap = new HashMap<>();
        for (MemoryEntity e : entities) {
            entityMap.put(e.getId(), e);
        }

        Set<Long> relatedEntityIds = new HashSet<>();
        List<MemoryNodeDTO.MemoryRelationDTO> relationDTOs = new ArrayList<>();
        for (MemoryNodeEntity r : relations) {
            MemoryNodeDTO.MemoryRelationDTO rd = new MemoryNodeDTO.MemoryRelationDTO();
            rd.setId(r.getId());
            rd.setEntityId(r.getEntityId());
            rd.setDescription(r.getDescription());
            relationDTOs.add(rd);
            relatedEntityIds.add(r.getEntityId());
        }

        List<MemoryNodeDTO.MemoryEntityDTO> entityDTOs = new ArrayList<>();
        for (Long entityId : relatedEntityIds) {
            MemoryEntity e = entityMap.get(entityId);
            if (e == null) continue;
            MemoryNodeDTO.MemoryEntityDTO ed = new MemoryNodeDTO.MemoryEntityDTO();
            ed.setId(e.getId());
            ed.setName(e.getName());
            entityDTOs.add(ed);
        }

        dto.setEntities(entityDTOs);
        dto.setRelations(relationDTOs);
        return dto;
    }

    private List<float[]> computeVectors(Long agentId, String content, List<String> descriptions) {
        Long embeddingModelId = getEmbeddingModelId(agentId);
        if (embeddingModelId == null) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        texts.add(content);
        texts.addAll(descriptions);
        try {
            return embeddingService.embed(embeddingModelId, texts);
        } catch (Exception e) {
            throw new IllegalStateException("记忆向量生成失败: " + e.getMessage(), e);
        }
    }

    private Long getEmbeddingModelId(Long agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        return agent != null ? agent.getEmbeddingModelId() : null;
    }

    private void registerAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("Lucene 索引提交失败，记忆数据与索引可能不一致，建议手动重建索引", e);
                    throw e;
                }
            }
        });
    }

    private void validateType(String type) {
        if (type == null || !VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException("记忆类型必须是 fact、preference、rule 之一");
        }
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? 3 : limit;
        if (value < 1) value = 1;
        if (value > 20) value = 20;
        return value;
    }

    private void ensureAgentExists(Long agentId) {
        if (!agentRepository.existsById(agentId)) {
            throw new IllegalArgumentException("智能体不存在");
        }
    }

    private void lock(Long agentId) {
        locks.computeIfAbsent(agentId, k -> new ReentrantLock()).lock();
    }

    private void unlock(Long agentId) {
        ReentrantLock lock = locks.get(agentId);
        if (lock != null) {
            lock.unlock();
        }
    }
}
