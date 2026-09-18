package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.MemoryEntityDTO;
import com.gacfox.meowclaw.dto.MemoryGraphDTO;
import com.gacfox.meowclaw.dto.MemoryNodeDTO;
import com.gacfox.meowclaw.dto.MemoryWriteDecision;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.MemoryEntity;
import com.gacfox.meowclaw.entity.MemoryNode;
import com.gacfox.meowclaw.entity.MemoryNodeEntity;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.MemoryEntityRepository;
import com.gacfox.meowclaw.repository.MemoryNodeEntityRepository;
import com.gacfox.meowclaw.repository.MemoryNodeRepository;
import com.gacfox.meowclaw.util.RrfFusionUtil;
import com.gacfox.proarc.common.model.Pagination;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
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
import java.util.stream.Collectors;

/**
 * 记忆核心服务：写入（含相似记忆决策）、召回、遗忘、管理面增删改查。
 */
@Slf4j
@Service
public class MemoryService {

    private static final Set<String> VALID_TYPES = Set.of("fact", "preference", "rule");
    private static final int WRITE_RECALL_LIMIT = 5;

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

    /**
     * 异步写入记忆：由线程池执行后立即返回，不阻塞调用方；同一agent的写入仍由锁串行执行
     */
    @Async
    public void writeAsync(Long agentId, String type, String content, Long conversationId) {
        try {
            write(agentId, type, content, conversationId);
        } catch (Exception e) {
            log.error("异步记忆写入失败: agentId={}, content={}", agentId, content, e);
        }
    }

    /**
     * 写入记忆：先召回相似记忆，由辅助LLM决策插入/跳过/更新/删除后单事务应用，返回操作摘要
     */
    public String write(Long agentId, String type, String content, Long conversationId) {
        validateType(type);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("记忆内容不能为空");
        }
        ensureAgentExists(agentId);
        lock(agentId);
        try {
            List<MemoryNodeDTO> similar = recallInternal(agentId, content, WRITE_RECALL_LIMIT, false);
            MemoryWriteDecision decision;
            try {
                decision = memoryExtractionService.decide(agentId, type, content, similar, conversationId);
            } catch (Exception e) {
                log.warn("记忆写入决策失败，降级为直接插入: agentId={}, error={}", agentId, e.getMessage());
                decision = MemoryWriteDecision.plainAdd(type, content);
            }
            if (!VALID_TYPES.contains(decision.getType())) {
                decision.setType(type);
            }

            Set<Long> similarIds = similar.stream().map(MemoryNodeDTO::getId).collect(Collectors.toSet());
            List<Long> deletes = decision.getDeletes() == null ? List.of() : decision.getDeletes().stream()
                    .filter(Objects::nonNull).filter(similarIds::contains).distinct().toList();
            List<MemoryWriteDecision.MemoryUpdate> updates = decision.getUpdates() == null ? List.of()
                    : decision.getUpdates().stream()
                    .filter(u -> u.getMemoryId() != null && similarIds.contains(u.getMemoryId()))
                    .filter(u -> u.getContent() != null && !u.getContent().isBlank())
                    .filter(u -> !deletes.contains(u.getMemoryId()))
                    .toList();
            boolean skipInsert = Boolean.TRUE.equals(decision.getDuplicate());

            List<MemoryWriteDecision.ExtractedRelation> validRelations = decision.getRelations() == null ? List.of()
                    : decision.getRelations().stream()
                    .filter(r -> r.getEntityName() != null && r.getDescription() != null)
                    .toList();
            List<float[]> insertVectors = skipInsert ? List.of()
                    : computeVectors(agentId, decision.getContent(),
                    validRelations.stream().map(r -> r.getDescription().trim()).toList());
            List<float[]> updateVectors = embedContents(agentId,
                    updates.stream().map(u -> u.getContent().trim()).toList());
            MemoryWriteDecision finalDecision = decision;

            return transactionTemplate.execute(status -> {
                for (Long deleteId : deletes) {
                    deleteNodeInternal(agentId, deleteId);
                }
                List<MemoryNode> updatedNodes = new ArrayList<>();
                for (MemoryWriteDecision.MemoryUpdate update : updates) {
                    updatedNodes.add(updateContentInternal(agentId, update.getMemoryId(), update.getContent().trim()));
                }
                MemoryNode inserted = null;
                List<MemoryNodeEntity> insertedRelations = List.of();
                if (!skipInsert) {
                    inserted = insertNodeInternal(agentId, finalDecision, validRelations);
                    insertedRelations = memoryNodeEntityRepository.findByNodeId(inserted.getId());
                }

                MemoryNode finalInserted = inserted;
                List<MemoryNodeEntity> finalInsertedRelations = insertedRelations;
                registerAfterCommit(() -> {
                    for (Long deleteId : deletes) {
                        luceneWriter.deleteNode(agentId, deleteId);
                    }
                    for (int i = 0; i < updatedNodes.size(); i++) {
                        float[] v = i < updateVectors.size() ? updateVectors.get(i) : null;
                        luceneWriter.addNode(agentId, updatedNodes.get(i), v);
                    }
                    if (finalInserted != null) {
                        float[] contentVector = insertVectors.isEmpty() ? null : insertVectors.get(0);
                        luceneWriter.addNode(agentId, finalInserted, contentVector);
                        List<MemoryLuceneWriter.MemoryNodeEntityWithVector> relationWithVectors = new ArrayList<>();
                        for (int i = 0; i < finalInsertedRelations.size(); i++) {
                            float[] v = insertVectors.isEmpty() || i + 1 >= insertVectors.size()
                                    ? null : insertVectors.get(i + 1);
                            relationWithVectors.add(new MemoryLuceneWriter.MemoryNodeEntityWithVector(
                                    finalInsertedRelations.get(i), v));
                        }
                        luceneWriter.addRelations(agentId, relationWithVectors);
                    }
                    luceneWriter.commit(agentId);
                });

                return buildWriteSummary(finalInserted, updates, deletes);
            });
        } finally {
            unlock(agentId);
        }
    }

    @Transactional
    public List<MemoryNodeDTO> recall(Long agentId, String query, Integer limit) {
        return recallInternal(agentId, query, limit, true);
    }

    private List<MemoryNodeDTO> recallInternal(Long agentId, String query, Integer limit, boolean updateAccessTime) {
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
        return loadNodesWithRelations(agentId, fused, updateAccessTime);
    }

    public void forget(Long agentId, Long nodeId) {
        if (nodeId == null) {
            throw new IllegalArgumentException("记忆ID不能为空");
        }
        ensureAgentExists(agentId);
        lock(agentId);
        try {
            transactionTemplate.execute(status -> {
                deleteNodeInternal(agentId, nodeId);
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

    @Transactional(readOnly = true)
    public Pagination<MemoryNodeDTO> list(Long agentId, String type, String keyword, Long entityId, int page, int size) {
        ensureAgentExists(agentId);
        String queryType = (type == null || type.isBlank()) ? null : type;
        String queryKeyword = (keyword == null || keyword.isBlank()) ? null : "%" + keyword + "%";
        Page<MemoryNode> pageResult = memoryNodeRepository.search(
                agentId, queryType, queryKeyword, entityId, PageRequest.of(page - 1, size));
        List<Long> ids = pageResult.getContent().stream().map(MemoryNode::getId).toList();
        List<MemoryNodeDTO> list = loadNodesWithRelations(agentId, ids, false);
        int total = (int) pageResult.getTotalElements();
        int totalPages = (int) Math.ceil((double) total / size);
        return new Pagination<>(list, total, totalPages, page, size);
    }

    /**
     * 召回预览：与memory_recall同一检索链路，但不更新访问时间，供管理页调试使用
     */
    @Transactional(readOnly = true)
    public List<MemoryNodeDTO> recallPreview(Long agentId, String query, Integer limit) {
        return recallInternal(agentId, query, limit, false);
    }

    /**
     * 列出智能体的全部实体及其关联记忆数，按记忆数降序
     */
    @Transactional(readOnly = true)
    public List<MemoryEntityDTO> listEntities(Long agentId) {
        ensureAgentExists(agentId);
        Map<Long, Long> countMap = new HashMap<>();
        for (Object[] row : memoryNodeEntityRepository.countByEntityForAgent(agentId)) {
            countMap.put((Long) row[0], (Long) row[1]);
        }
        return memoryEntityRepository.findByAgentId(agentId).stream()
                .map(e -> new MemoryEntityDTO(e.getId(), e.getName(), countMap.getOrDefault(e.getId(), 0L)))
                .sorted((a, b) -> Long.compare(b.getMemoryCount(), a.getMemoryCount()))
                .toList();
    }

    /**
     * 记忆-实体关系图谱数据：取最近100条记忆及其关系
     */
    @Transactional(readOnly = true)
    public MemoryGraphDTO graph(Long agentId) {
        ensureAgentExists(agentId);
        Page<MemoryNode> pageResult = memoryNodeRepository.search(agentId, null, null, null,
                PageRequest.of(0, 100));
        List<MemoryNode> nodes = pageResult.getContent();
        List<Long> nodeIds = nodes.stream().map(MemoryNode::getId).toList();

        List<MemoryNodeEntity> relations = nodeIds.isEmpty() ? List.of()
                : memoryNodeEntityRepository.findByNodeIdIn(nodeIds);
        Set<Long> entityIds = relations.stream().map(MemoryNodeEntity::getEntityId).collect(Collectors.toSet());
        Map<Long, MemoryEntity> entityMap = new HashMap<>();
        for (MemoryEntity e : memoryEntityRepository.findAllById(entityIds)) {
            entityMap.put(e.getId(), e);
        }

        MemoryGraphDTO graph = new MemoryGraphDTO();
        graph.setMemories(nodes.stream()
                .map(n -> new MemoryGraphDTO.GraphMemory(n.getId(), n.getType(), n.getContent()))
                .toList());
        graph.setEntities(entityMap.values().stream()
                .map(e -> new MemoryGraphDTO.GraphEntity(e.getId(), e.getName()))
                .toList());
        graph.setRelations(relations.stream()
                .map(r -> new MemoryGraphDTO.GraphRelation(r.getNodeId(), r.getEntityId(), r.getDescription()))
                .toList());
        return graph;
    }

    /**
     * 手动创建记忆：内容原样直写，不经过LLM抽取
     */
    public MemoryNodeDTO createManual(Long agentId, String type, String content) {
        validateType(type);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("记忆内容不能为空");
        }
        ensureAgentExists(agentId);
        lock(agentId);
        try {
            float[] vector = embedSingle(agentId, content);
            return transactionTemplate.execute(status -> {
                long now = System.currentTimeMillis();
                MemoryNode node = new MemoryNode();
                node.setAgentId(agentId);
                node.setType(type);
                node.setContent(content);
                node.setCreatedAt(now);
                node.setUpdatedAt(now);
                MemoryNode saved = memoryNodeRepository.save(node);
                registerAfterCommit(() -> {
                    luceneWriter.addNode(agentId, saved, vector);
                    luceneWriter.commit(agentId);
                });
                return toNodeDTO(saved, List.of(), List.of());
            });
        } finally {
            unlock(agentId);
        }
    }

    /**
     * 手动编辑记忆：仅允许修改类型与内容，实体关系保持不变
     */
    public MemoryNodeDTO updateManual(Long id, String type, String content) {
        MemoryNode node = memoryNodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记忆不存在"));
        if (type != null) {
            validateType(type);
        }
        if (content != null && content.isBlank()) {
            throw new IllegalArgumentException("记忆内容不能为空");
        }
        Long agentId = node.getAgentId();
        lock(agentId);
        try {
            String newContent = content != null ? content : node.getContent();
            float[] vector = embedSingle(agentId, newContent);
            transactionTemplate.execute(status -> {
                if (type != null) {
                    node.setType(type);
                }
                node.setContent(newContent);
                node.setUpdatedAt(System.currentTimeMillis());
                memoryNodeRepository.save(node);
                registerAfterCommit(() -> {
                    luceneWriter.addNode(agentId, node, vector);
                    luceneWriter.commit(agentId);
                });
                return null;
            });
            return loadNodesWithRelations(agentId, List.of(id), false).get(0);
        } finally {
            unlock(agentId);
        }
    }

    public void deleteManual(Long id) {
        MemoryNode node = memoryNodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记忆不存在"));
        forget(node.getAgentId(), id);
    }

    private MemoryNode insertNodeInternal(Long agentId, MemoryWriteDecision decision,
                                          List<MemoryWriteDecision.ExtractedRelation> validRelations) {
        long now = System.currentTimeMillis();

        List<String> entityNames = decision.getEntities() == null ? List.of()
                : decision.getEntities().stream()
                .map(MemoryWriteDecision.ExtractedEntity::getName)
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
            for (MemoryEntity e : memoryEntityRepository.saveAll(newEntities)) {
                entityByName.put(e.getName(), e);
            }
        }

        MemoryNode node = new MemoryNode();
        node.setAgentId(agentId);
        node.setType(decision.getType());
        node.setContent(decision.getContent());
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        node = memoryNodeRepository.save(node);

        List<MemoryNodeEntity> relations = new ArrayList<>();
        for (MemoryWriteDecision.ExtractedRelation rel : validRelations) {
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
        if (!relations.isEmpty()) {
            memoryNodeEntityRepository.saveAll(relations);
        }
        return node;
    }

    private void deleteNodeInternal(Long agentId, Long nodeId) {
        MemoryNode node = memoryNodeRepository.findByAgentIdAndIdIn(agentId, List.of(nodeId))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("记忆不存在: " + nodeId));

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
    }

    private MemoryNode updateContentInternal(Long agentId, Long nodeId, String content) {
        MemoryNode node = memoryNodeRepository.findByAgentIdAndIdIn(agentId, List.of(nodeId))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("记忆不存在: " + nodeId));
        node.setContent(content);
        node.setUpdatedAt(System.currentTimeMillis());
        return memoryNodeRepository.save(node);
    }

    private String buildWriteSummary(MemoryNode inserted, List<MemoryWriteDecision.MemoryUpdate> updates,
                                     List<Long> deletes) {
        StringBuilder summary = new StringBuilder();
        if (inserted != null) {
            summary.append("记忆已写入(id=").append(inserted.getId()).append(")");
        } else {
            summary.append("新记忆与已有记忆重复，跳过写入");
        }
        if (!updates.isEmpty()) {
            summary.append("，更新记忆");
            summary.append(updates.stream().map(u -> "id=" + u.getMemoryId()).collect(Collectors.joining("、", "(", ")")));
        }
        if (!deletes.isEmpty()) {
            summary.append("，删除记忆");
            summary.append(deletes.stream().map(id -> "id=" + id).collect(Collectors.joining("、", "(", ")")));
        }
        return summary.toString();
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

    private float[] embedSingle(Long agentId, String text) {
        Long embeddingModelId = getEmbeddingModelId(agentId);
        if (embeddingModelId == null) {
            return null;
        }
        List<float[]> results = embeddingService.embed(embeddingModelId, List.of(text));
        return results.isEmpty() ? null : results.get(0);
    }

    private List<float[]> embedContents(Long agentId, List<String> contents) {
        if (contents.isEmpty()) {
            return List.of();
        }
        Long embeddingModelId = getEmbeddingModelId(agentId);
        if (embeddingModelId == null) {
            return List.of();
        }
        return embeddingService.embed(embeddingModelId, contents);
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
