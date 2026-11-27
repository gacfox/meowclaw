package com.gacfox.meowclaw.service.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.SearcherManager;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;

@Getter
@AllArgsConstructor
public class AgentMemoryIndex {
    private final IKAnalyzer analyzer;
    private final IndexWriter nodeWriter;
    private final IndexWriter relationWriter;
    private final SearcherManager nodeSearcherManager;
    private final SearcherManager relationSearcherManager;

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
