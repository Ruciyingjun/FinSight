/**
 * 检索重排序器：综合原始排名、查询词覆盖率和文本密度，并用相对阈值去除弱相关证据。
 */
package com.financialresearch.retrieval;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class RetrievalReranker {

    private final SearchTokenizer tokenizer;
    private final RetrievalProperties properties;

    public RetrievalReranker(SearchTokenizer tokenizer, RetrievalProperties properties) {
        this.tokenizer = tokenizer;
        this.properties = properties;
    }

    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int limit) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<String> queryTokens = meaningfulQueryTokens(query);
        double highestSourceScore = candidates.stream()
                .mapToDouble(RetrievalResult::score)
                .max()
                .orElse(1.0);

        List<ScoredCandidate> ranked = candidates.stream()
                .map(candidate -> withRerankScore(candidate, queryTokens, highestSourceScore))
                .sorted(java.util.Comparator.comparingDouble((ScoredCandidate scored) -> scored.result().score()).reversed()
                        .thenComparing(scored -> scored.result().documentId())
                        .thenComparingInt(scored -> scored.result().chunkIndex()))
                .toList();
        double threshold = Math.max(properties.absoluteThreshold(),
                ranked.getFirst().result().score() * properties.relativeThreshold());
        return ranked.stream()
                .filter(scored -> scored.result().score() >= threshold)
                .filter(scored -> scored.queryCoverage() >= properties.minimumQueryCoverage())
                .map(ScoredCandidate::result)
                .limit(limit)
                .toList();
    }

    public List<RetrievalResult> selectSummaryEvidence(List<RetrievalResult> candidates, int limit) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<RetrievalResult> selected = new ArrayList<>();
        Set<String> selectedPages = new LinkedHashSet<>();
        for (RetrievalResult candidate : candidates) {
            String pageKey = candidate.documentId() + ":" + candidate.pageNumber();
            if (selectedPages.add(pageKey)) {
                selected.add(candidate);
                if (selected.size() == limit) {
                    return List.copyOf(selected);
                }
            }
        }
        for (RetrievalResult candidate : candidates) {
            if (!selected.contains(candidate)) {
                selected.add(candidate);
                if (selected.size() == limit) {
                    break;
                }
            }
        }
        return List.copyOf(selected);
    }

    private ScoredCandidate withRerankScore(
            RetrievalResult candidate, Set<String> queryTokens, double highestSourceScore) {
        Set<String> contentTokens = new HashSet<>(tokenizer.tokenize(candidate.content()));
        long matches = queryTokens.stream().filter(contentTokens::contains).count();
        double coverage = queryTokens.isEmpty() ? 0 : (double) matches / queryTokens.size();
        double sourceScore = highestSourceScore <= 0 ? 0 : candidate.score() / highestSourceScore;
        double density = contentTokens.isEmpty() ? 0 : Math.min(1.0, (double) matches / contentTokens.size() * 4);
        double score = coverage * 0.65 + sourceScore * 0.25 + density * 0.10;
        RetrievalResult result = new RetrievalResult(
                candidate.documentId(), candidate.chunkIndex(), candidate.pageNumber(),
                candidate.startOffset(), candidate.endOffset(), candidate.content(), score);
        return new ScoredCandidate(result, coverage);
    }

    private Set<String> meaningfulQueryTokens(String query) {
        Set<String> all = new HashSet<>(tokenizer.tokenize(query));
        Set<String> meaningful = all.stream()
                .filter(token -> token.codePointCount(0, token.length()) >= 2)
                .collect(java.util.stream.Collectors.toSet());
        return meaningful.isEmpty() ? all : meaningful;
    }

    private record ScoredCandidate(RetrievalResult result, double queryCoverage) {
    }
}
