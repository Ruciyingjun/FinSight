/**
 * 向量检索器：计算查询向量与持久化文本块向量的余弦相似度并返回最相关结果。
 */
package com.financialresearch.retrieval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.financialresearch.document.chunk.DocumentChunkService;
import com.financialresearch.document.chunk.EmbeddedDocumentChunk;
import com.financialresearch.embedding.EmbeddingProvider;
import com.financialresearch.embedding.EmbeddingVector;

@Component
public class VectorChunkRetriever implements ChunkRetriever {

    private final DocumentChunkService chunkService;
    private final EmbeddingProvider embeddingProvider;

    public VectorChunkRetriever(DocumentChunkService chunkService, EmbeddingProvider embeddingProvider) {
        this.chunkService = chunkService;
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public List<RetrievalResult> retrieve(RetrievalQuery query) {
        EmbeddingVector queryVector = embeddingProvider.embed(query.text());
        return chunkService.listEmbedded(query.documentId()).stream()
                .filter(chunk -> queryVector.model().equals(chunk.embeddingModel()))
                .map(chunk -> toResult(chunk, cosine(queryVector.values(), chunk.embedding())))
                .filter(result -> result.score() > 0)
                .sorted(java.util.Comparator.comparingDouble(RetrievalResult::score).reversed()
                        .thenComparing(RetrievalResult::documentId)
                        .thenComparingInt(RetrievalResult::chunkIndex))
                .limit(query.limit())
                .toList();
    }

    private RetrievalResult toResult(EmbeddedDocumentChunk chunk, double score) {
        return new RetrievalResult(
                chunk.documentId(), chunk.chunkIndex(), chunk.pageNumber(),
                chunk.startOffset(), chunk.endOffset(), chunk.content(), score);
    }

    private double cosine(float[] left, float[] right) {
        if (left.length == 0 || left.length != right.length) {
            return 0;
        }
        double dotProduct = 0;
        double leftMagnitude = 0;
        double rightMagnitude = 0;
        for (int index = 0; index < left.length; index++) {
            dotProduct += left[index] * right[index];
            leftMagnitude += left[index] * left[index];
            rightMagnitude += right[index] * right[index];
        }
        if (leftMagnitude == 0 || rightMagnitude == 0) {
            return 0;
        }
        return dotProduct / Math.sqrt(leftMagnitude * rightMagnitude);
    }
}
