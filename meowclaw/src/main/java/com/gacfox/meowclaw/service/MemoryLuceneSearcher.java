package com.gacfox.meowclaw.service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 记忆 Lucene 检索：BM25 + 向量混合检索。
 */
@Component
public class MemoryLuceneSearcher {

    private final MemoryIndexManager indexManager;

    @Autowired
    public MemoryLuceneSearcher(MemoryIndexManager indexManager) {
        this.indexManager = indexManager;
    }

    /**
     * 直接检索记忆节点索引。
     */
    public List<Long> searchNodes(Long agentId, String queryText, float[] queryVector, int topK) {
        return searchIndex(indexManager.getIndex(agentId).nodeSearcherManager(),
                "content", "content_vector", "id", agentId, queryText, queryVector, topK, null);
    }

    /**
     * 通过关系索引召回相关的 memory_node_id，去重后最多返回 maxNodeIds 个。
     */
    public List<Long> searchRelationNodeIds(Long agentId, String queryText, float[] queryVector,
                                             int relationTopK, int maxNodeIds) {
        List<Long> relationHits = searchIndex(indexManager.getIndex(agentId).relationSearcherManager(),
                "description", "description_vector", "memory_node_id", agentId, queryText, queryVector, relationTopK, null);
        Set<Long> nodeIds = new LinkedHashSet<>();
        for (Long nodeId : relationHits) {
            if (nodeId == null) continue;
            nodeIds.add(nodeId);
            if (nodeIds.size() >= maxNodeIds) break;
        }
        return new ArrayList<>(nodeIds);
    }

    /**
     * 通过关系索引召回相关的 entity_id，去重后最多返回 maxEntityIds 个。
     */
    public List<Long> searchRelationEntityIds(Long agentId, String queryText, float[] queryVector,
                                                int relationTopK, int maxEntityIds) {
        List<Long> relationHits = searchIndex(indexManager.getIndex(agentId).relationSearcherManager(),
                "description", "description_vector", "entity_id", agentId, queryText, queryVector, relationTopK, null);
        Set<Long> entityIds = new LinkedHashSet<>();
        for (Long entityId : relationHits) {
            if (entityId == null) continue;
            entityIds.add(entityId);
            if (entityIds.size() >= maxEntityIds) break;
        }
        return new ArrayList<>(entityIds);
    }

    /**
     * 在指定节点 ID 集合内检索节点索引。
     */
    public List<Long> searchNodesInSet(Long agentId, String queryText, float[] queryVector,
                                        Set<Long> nodeIds, int topK) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        BooleanQuery.Builder idFilter = new BooleanQuery.Builder();
        for (Long nodeId : nodeIds) {
            idFilter.add(new TermQuery(new Term("id", String.valueOf(nodeId))), BooleanClause.Occur.SHOULD);
        }
        idFilter.setMinimumNumberShouldMatch(1);
        return searchIndex(indexManager.getIndex(agentId).nodeSearcherManager(),
                "content", "content_vector", "id", agentId, queryText, queryVector, topK, idFilter.build());
    }

    private List<Long> searchIndex(SearcherManager searcherManager, String textField, String vectorField,
                                    String resultField, Long agentId, String queryText, float[] queryVector,
                                    int topK, Query extraFilter) {
        try {
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                BooleanQuery.Builder bq = new BooleanQuery.Builder();
                Query agentFilter = new TermQuery(new Term("agent_id", String.valueOf(agentId)));
                bq.add(agentFilter, BooleanClause.Occur.MUST);

                if (extraFilter != null) {
                    bq.add(extraFilter, BooleanClause.Occur.MUST);
                }

                if (queryText != null && !queryText.isBlank()) {
                    String safe = QueryParser.escape(queryText.trim());
                    Analyzer analyzer = indexManager.getIndex(agentId).analyzer();
                    Query textQuery = new QueryParser(textField, analyzer).parse(safe);
                    bq.add(textQuery, BooleanClause.Occur.SHOULD);
                }

                if (queryVector != null) {
                    Query filter = buildFilter(agentFilter, extraFilter);
                    bq.add(new KnnFloatVectorQuery(vectorField, queryVector, topK, filter), BooleanClause.Occur.SHOULD);
                }

                TopDocs docs = searcher.search(bq.build(), topK);
                IndexReader reader = searcher.getIndexReader();
                List<Long> ids = new ArrayList<>();
                for (ScoreDoc sd : docs.scoreDocs) {
                    Document doc = reader.storedFields().document(sd.doc);
                    String val = doc.get(resultField);
                    if (val != null) {
                        ids.add(Long.parseLong(val));
                    }
                }
                return ids;
            } finally {
                searcherManager.release(searcher);
            }
        } catch (ParseException e) {
            return List.of();
        } catch (IOException e) {
            throw new RuntimeException("记忆索引检索失败", e);
        }
    }

    private Query buildFilter(Query agentFilter, Query extraFilter) {
        if (extraFilter == null) {
            return agentFilter;
        }
        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        bq.add(agentFilter, BooleanClause.Occur.MUST);
        bq.add(extraFilter, BooleanClause.Occur.MUST);
        return bq.build();
    }
}
