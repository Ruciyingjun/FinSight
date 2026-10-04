/**
 * 问答上下文组装器测试：验证相邻重叠文本合并和引用编号保留。
 */
package com.financialresearch.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.financialresearch.retrieval.RetrievalProperties;

class QuestionContextAssemblerTest {

    private final QuestionContextAssembler assembler = new QuestionContextAssembler(
            new RetrievalProperties(4, 20, 0.35, 0.40, 0.20, 6000));

    @Test
    void mergesAdjacentOverlappingChunks() {
        CitationResponse first = citation(1, 0, 0, 6, "abcdef");
        CitationResponse second = citation(2, 1, 4, 10, "efghij");

        List<ContextPassage> passages = assembler.assemble(List.of(first, second));

        assertThat(passages).containsExactly(new ContextPassage(List.of(1, 2), "abcdefghij"));
    }

    private CitationResponse citation(int number, int chunkIndex, int start, int end, String quote) {
        return new CitationResponse(number, "document", 1, chunkIndex, start, end, quote, 1.0);
    }
}
