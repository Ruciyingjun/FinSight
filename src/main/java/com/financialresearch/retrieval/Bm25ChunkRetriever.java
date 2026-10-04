/**
 * 数据库 BM25 检索器：对持久化 chunks 计算词频、逆文档频率和长度归一化分数。
 */
package com.financialresearch.retrieval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.financialresearch.document.chunk.DocumentChunkResponse;
import com.financialresearch.document.chunk.DocumentChunkService;

@Component
public class Bm25ChunkRetriever implements ChunkRetriever {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final DocumentChunkService chunkService;
    private final SearchTokenizer tokenizer;

    public Bm25ChunkRetriever(DocumentChunkService chunkService, SearchTokenizer tokenizer) {
        this.chunkService = chunkService;
        this.tokenizer = tokenizer;
    }

    @Override
    public List<RetrievalResult> retrieve(RetrievalQuery query) {
        List<DocumentChunkResponse> candidates = candidates(query.documentId());
        List<String> queryTokens = tokenizer.tokenize(query.text());
        if (candidates.isEmpty() || queryTokens.isEmpty()) {
            return List.of();
        }

        List<TokenizedChunk> corpus = candidates.stream()
                .map(chunk -> new TokenizedChunk(chunk, tokenizer.tokenize(chunk.content())))
                .toList();
        double averageLength = corpus.stream().mapToInt(chunk -> chunk.tokens().size()).average().orElse(1.0);
        Map<String, Long> documentFrequency = documentFrequency(corpus, new HashSet<>(queryTokens));
        Map<String, Long> queryFrequency = queryTokens.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<RetrievalResult> results = new ArrayList<>();
        for (TokenizedChunk candidate : corpus) {
            double score = score(candidate.tokens(), queryFrequency, documentFrequency, corpus.size(), averageLength);
            if (score > 0) {
                DocumentChunkResponse chunk = candidate.chunk();
                results.add(new RetrievalResult(
                        chunk.documentId(), chunk.chunkIndex(), chunk.pageNumber(),
                        chunk.startOffset(), chunk.endOffset(), chunk.content(), score));
            }
        }
        return results.stream()
                .sorted(java.util.Comparator.comparingDouble(RetrievalResult::score).reversed()
                        .thenComparing(RetrievalResult::documentId)
                        .thenComparingInt(RetrievalResult::chunkIndex))
                .limit(query.limit())
                .toList();
    }

    private List<DocumentChunkResponse> candidates(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return chunkService.listAll();
        }
        return chunkService.list(documentId);
    }

    private Map<String, Long> documentFrequency(List<TokenizedChunk> corpus, Set<String> queryTokens) {
        Map<String, Long> frequencies = new HashMap<>();
        for (String token : queryTokens) {
            long count = corpus.stream().filter(chunk -> chunk.tokens().contains(token)).count();
            frequencies.put(token, count);
        }
        return frequencies;
    }

    private double score(List<String> tokens, Map<String, Long> queryFrequency,
            Map<String, Long> documentFrequency, int corpusSize, double averageLength) {
        Map<String, Long> termFrequency = tokens.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        double score = 0;
        for (Map.Entry<String, Long> queryTerm : queryFrequency.entrySet()) {
            double tf = termFrequency.getOrDefault(queryTerm.getKey(), 0L);
            if (tf == 0) {
                continue;
            }
            double df = documentFrequency.getOrDefault(queryTerm.getKey(), 0L);
            double idf = Math.log(1 + (corpusSize - df + 0.5) / (df + 0.5));
            double lengthNormalization = tf + K1 * (1 - B + B * tokens.size() / averageLength);
            score += idf * (tf * (K1 + 1) / lengthNormalization) * queryTerm.getValue();
        }
        return score;
    }

    private record TokenizedChunk(DocumentChunkResponse chunk, List<String> tokens) {
    }
}
