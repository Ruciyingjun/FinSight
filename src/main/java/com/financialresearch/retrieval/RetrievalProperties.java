/**
 * 检索配置：控制问答候选集规模、动态相关性阈值以及上下文总长度。
 */
package com.financialresearch.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.retrieval")
public record RetrievalProperties(
        int candidateMultiplier,
        int minimumCandidates,
        double relativeThreshold,
        double absoluteThreshold,
        double minimumQueryCoverage,
        int maximumContextCharacters) {

    public RetrievalProperties {
        if (candidateMultiplier < 1 || candidateMultiplier > 20) {
            throw new IllegalArgumentException("candidateMultiplier 必须在 1 到 20 之间");
        }
        if (minimumCandidates < 1 || minimumCandidates > 50) {
            throw new IllegalArgumentException("minimumCandidates 必须在 1 到 50 之间");
        }
        if (relativeThreshold < 0 || relativeThreshold > 1) {
            throw new IllegalArgumentException("relativeThreshold 必须在 0 到 1 之间");
        }
        if (absoluteThreshold < 0 || absoluteThreshold > 1) {
            throw new IllegalArgumentException("absoluteThreshold 必须在 0 到 1 之间");
        }
        if (minimumQueryCoverage < 0 || minimumQueryCoverage > 1) {
            throw new IllegalArgumentException("minimumQueryCoverage 必须在 0 到 1 之间");
        }
        if (maximumContextCharacters < 500 || maximumContextCharacters > 50000) {
            throw new IllegalArgumentException("maximumContextCharacters 必须在 500 到 50000 之间");
        }
    }
}
