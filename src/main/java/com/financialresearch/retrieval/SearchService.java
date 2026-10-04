/**
 * 检索应用服务：校验文档范围并将查询委托给当前配置的文本块检索器。
 */
package com.financialresearch.retrieval;

import java.util.List;

import org.springframework.stereotype.Service;

import com.financialresearch.document.DocumentService;

@Service
public class SearchService {

    private final Bm25ChunkRetriever keywordRetriever;
    private final VectorChunkRetriever vectorRetriever;
    private final HybridChunkRetriever hybridRetriever;
    private final DocumentService documentService;

    public SearchService(Bm25ChunkRetriever keywordRetriever,
            VectorChunkRetriever vectorRetriever,
            HybridChunkRetriever hybridRetriever,
            DocumentService documentService) {
        this.keywordRetriever = keywordRetriever;
        this.vectorRetriever = vectorRetriever;
        this.hybridRetriever = hybridRetriever;
        this.documentService = documentService;
    }

    public List<RetrievalResult> search(String query, String documentId, int limit) {
        return search(query, documentId, limit, RetrievalMode.HYBRID);
    }

    public List<RetrievalResult> search(String query, String documentId, int limit, RetrievalMode mode) {
        if (documentId != null && !documentId.isBlank()) {
            documentService.get(documentId);
        }
        RetrievalQuery retrievalQuery = new RetrievalQuery(query, documentId, limit);
        return switch (mode) {
            case KEYWORD -> keywordRetriever.retrieve(retrievalQuery);
            case VECTOR -> vectorRetriever.retrieve(retrievalQuery);
            case HYBRID -> hybridRetriever.retrieve(retrievalQuery);
        };
    }
}
