/**
 * 检索重排序器测试：验证查询覆盖率重排和动态阈值能够过滤弱相关候选。
 */
package com.financialresearch.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RetrievalRerankerTest {

    private final RetrievalReranker reranker = new RetrievalReranker(
            new SearchTokenizer(), new RetrievalProperties(4, 20, 0.35, 0.40, 0.20, 6000));

    @Test
    void promotesTokenCoverageAndDropsWeakCandidate() {
        RetrievalResult weak = result(0, "无关的市场信息", 1.0);
        RetrievalResult relevant = result(1, "保险责任包括医疗保障", 0.8);

        List<RetrievalResult> results = reranker.rerank("保险责任", List.of(weak, relevant), 5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().chunkIndex()).isEqualTo(1);
    }

    @Test
    void rejectsTopRankedCandidateWithoutMeaningfulQueryOverlap() {
        RetrievalResult unrelated = result(0, "公司营业收入和市场销售情况", 1.0);

        List<RetrievalResult> results = reranker.rerank("教育经历", List.of(unrelated), 5);

        assertThat(results).isEmpty();
    }

    @Test
    void summaryEvidenceDoesNotRequireLiteralQueryOverlapAndCoversDifferentPages() {
        RetrievalResult firstPage = result(0, 1, "候选人的教育背景", 1.0);
        RetrievalResult duplicatePage = result(1, 1, "候选人的专业技能", 0.9);
        RetrievalResult secondPage = result(2, 2, "候选人的项目经历", 0.8);

        List<RetrievalResult> results = reranker.selectSummaryEvidence(
                List.of(firstPage, duplicatePage, secondPage), 2);

        assertThat(results).extracting(RetrievalResult::pageNumber).containsExactly(1, 2);
    }

    private RetrievalResult result(int chunkIndex, String content, double score) {
        return result(chunkIndex, 1, content, score);
    }

    private RetrievalResult result(int chunkIndex, int pageNumber, String content, double score) {
        return new RetrievalResult("document", chunkIndex, pageNumber, 0, content.length(), content, score);
    }
}
