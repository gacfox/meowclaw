package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.MemoryNode;
import com.gacfox.meowclaw.entity.MemoryNodeEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 记忆 Lucene 文档写入/删除。
 */
@Slf4j
@Component
public class MemoryLuceneWriter {

    private final MemoryIndexManager indexManager;

    @Autowired
    public MemoryLuceneWriter(MemoryIndexManager indexManager) {
        this.indexManager = indexManager;
    }

    public void addNode(Long agentId, MemoryNode node, float[] contentVector) {
        Document doc = new Document();
        doc.add(new StringField("id", String.valueOf(node.getId()), Field.Store.YES));
        doc.add(new StringField("agent_id", String.valueOf(agentId), Field.Store.NO));
        doc.add(new StringField("type", node.getType(), Field.Store.YES));
        doc.add(new TextField("content", node.getContent(), Field.Store.YES));
        if (contentVector != null) {
            try {
                doc.add(new KnnFloatVectorField("content_vector", contentVector, VectorSimilarityFunction.COSINE));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("记忆向量维度与已有索引不一致，请清空记忆或改回原有嵌入模型", e);
            }
        }
        try {
            IndexWriter writer = indexManager.getIndex(agentId).nodeWriter();
            writer.updateDocument(new Term("id", String.valueOf(node.getId())), doc);
        } catch (IOException e) {
            throw new RuntimeException("写入记忆节点索引失败", e);
        }
    }

    public void addRelation(Long agentId, MemoryNodeEntity relation, float[] descriptionVector) {
        Document doc = new Document();
        doc.add(new StringField("id", String.valueOf(relation.getId()), Field.Store.YES));
        doc.add(new StringField("agent_id", String.valueOf(agentId), Field.Store.NO));
        doc.add(new StringField("memory_node_id", String.valueOf(relation.getNodeId()), Field.Store.YES));
        doc.add(new StringField("entity_id", String.valueOf(relation.getEntityId()), Field.Store.YES));
        doc.add(new TextField("description", relation.getDescription(), Field.Store.YES));
        if (descriptionVector != null) {
            try {
                doc.add(new KnnFloatVectorField("description_vector", descriptionVector, VectorSimilarityFunction.COSINE));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("关系向量维度与已有索引不一致，请清空记忆或改回原有嵌入模型", e);
            }
        }
        try {
            IndexWriter writer = indexManager.getIndex(agentId).relationWriter();
            writer.updateDocument(new Term("id", String.valueOf(relation.getId())), doc);
        } catch (IOException e) {
            throw new RuntimeException("写入记忆关系索引失败", e);
        }
    }

    public void addRelations(Long agentId, List<MemoryNodeEntityWithVector> relations) {
        for (MemoryNodeEntityWithVector rv : relations) {
            addRelation(agentId, rv.relation(), rv.descriptionVector());
        }
    }

    public void deleteNode(Long agentId, Long nodeId) {
        String idStr = String.valueOf(nodeId);
        try {
            MemoryIndexManager.AgentMemoryIndex index = indexManager.getIndex(agentId);
            index.nodeWriter().deleteDocuments(new Term("id", idStr));
            index.relationWriter().deleteDocuments(new Term("memory_node_id", idStr));
        } catch (IOException e) {
            throw new RuntimeException("删除记忆索引失败", e);
        }
    }

    public void commit(Long agentId) {
        try {
            indexManager.getIndex(agentId).commit();
        } catch (IOException e) {
            throw new RuntimeException("提交记忆索引失败", e);
        }
    }

    public record MemoryNodeEntityWithVector(MemoryNodeEntity relation, float[] descriptionVector) {
    }
}
