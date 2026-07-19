package com.gacfox.meowclaw.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按智能体维护 Lucene 索引的 writer 与 searcher。
 */
@Slf4j
@Component
public class MemoryIndexManager {

    private final String baseDir;
    private final Map<Long, AgentMemoryIndex> indexes = new ConcurrentHashMap<>();

    public MemoryIndexManager(@Value("${meowclaw.data-dir}") String dataDir) {
        this.baseDir = Paths.get(dataDir, "lucene", "memory").toString();
    }

    public AgentMemoryIndex getIndex(Long agentId) {
        return indexes.computeIfAbsent(agentId, this::createIndex);
    }

    private AgentMemoryIndex createIndex(Long agentId) {
        try {
            Path nodePath = Paths.get(baseDir, String.valueOf(agentId), "node");
            Path relPath = Paths.get(baseDir, String.valueOf(agentId), "node_entity");
            Files.createDirectories(nodePath);
            Files.createDirectories(relPath);

            IKAnalyzer analyzer = new IKAnalyzer(true);
            IndexWriterConfig nodeConfig = new IndexWriterConfig(analyzer);
            nodeConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            IndexWriterConfig relConfig = new IndexWriterConfig(analyzer);
            relConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            IndexWriter nodeWriter = new IndexWriter(FSDirectory.open(nodePath), nodeConfig);
            IndexWriter relWriter = new IndexWriter(FSDirectory.open(relPath), relConfig);
            SearcherManager nodeSearcher = new SearcherManager(nodeWriter, null);
            SearcherManager relSearcher = new SearcherManager(relWriter, null);

            return new AgentMemoryIndex(analyzer, nodeWriter, relWriter, nodeSearcher, relSearcher);
        } catch (LockObtainFailedException e) {
            throw new IllegalStateException("记忆索引被其他进程锁定: agentId=" + agentId, e);
        } catch (IOException e) {
            throw new UncheckedIOException("初始化记忆索引失败: agentId=" + agentId, e);
        }
    }

    @PreDestroy
    public void closeAll() {
        for (Map.Entry<Long, AgentMemoryIndex> entry : indexes.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                log.warn("关闭记忆索引失败: agentId={}", entry.getKey(), e);
            }
        }
        indexes.clear();
    }

    public record AgentMemoryIndex(
            IKAnalyzer analyzer,
            IndexWriter nodeWriter,
            IndexWriter relationWriter,
            SearcherManager nodeSearcherManager,
            SearcherManager relationSearcherManager
    ) {
        public void commit() throws IOException {
            nodeWriter.commit();
            relationWriter.commit();
            nodeSearcherManager.maybeRefreshBlocking();
            relationSearcherManager.maybeRefreshBlocking();
        }

        public void close() throws IOException {
            nodeSearcherManager.close();
            relationSearcherManager.close();
            nodeWriter.close();
            relationWriter.close();
            analyzer.close();
        }
    }
}
