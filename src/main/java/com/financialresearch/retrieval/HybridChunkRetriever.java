/**
 * 混合检索器：使用 Reciprocal Rank Fusion 融合 BM25 与向量检索的排名结果。
 */
package com.financialresearch.retrieval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class HybridChunkRetriever implements ChunkRetriever {

    private static final int RRF_CONSTANT = 60;

    private final Bm25ChunkRetriever keywordRetriever;
    private final VectorChunkRetriever vectorRetriever;

    public HybridChunkRetriever(Bm25ChunkRetriever keywordRetriever, VectorChunkRetriever vectorRetriever) {
        this.keywordRetriever = keywordRetriever;
        this.vectorRetriever = vectorRetriever;
    }

    @Override
    public List<RetrievalResult> retrieve(RetrievalQuery query) {
        int candidateLimit = Math.min(50, Math.max(20, query.limit() * 4));
        RetrievalQuery candidateQuery = new RetrievalQuery(query.text(), query.documentId(), candidateLimit);
        List<RetrievalResult> keywordResults = keywordRetriever.retrieve(candidateQuery);
        List<RetrievalResult> vectorResults = vectorRetriever.retrieve(candidateQuery);

        Map<ResultKey, FusedResult> fused = new HashMap<>();
        addRanking(fused, keywordResults);
        addRanking(fused, vectorResults);
        return fused.values().stream()
                .map(FusedResult::result)
                .sorted(java.util.Comparator.comparingDouble(RetrievalResult::score).reversed()
                        .thenComparing(RetrievalResult::documentId)
                        .thenComparingInt(RetrievalResult::chunkIndex))
                .limit(query.limit())
                .toList();
    }

    private void addRanking(Map<ResultKey, FusedResult> fused, List<RetrievalResult> ranking) {
        for (int index = 0; index < ranking.size(); index++) {
            RetrievalResult result = ranking.get(index);
            ResultKey key = new ResultKey(result.documentId(), result.chunkIndex());
            double contribution = 1.0 / (RRF_CONSTANT + index + 1);
            fused.compute(key, (ignored, current) -> {
                double score = contribution + (current == null ? 0 : current.score());
                RetrievalResult source = current == null ? result : current.source();
                return new FusedResult(source, score);
            });
        }
    }

    private record ResultKey(String documentId, int chunkIndex) {
    }

    private record FusedResult(RetrievalResult source, double score) {
        RetrievalResult result() {
            return new RetrievalResult(
                    source.documentId(), source.chunkIndex(), source.pageNumber(),
                    source.startOffset(), source.endOffset(), source.content(), score);
        }
    }
}
