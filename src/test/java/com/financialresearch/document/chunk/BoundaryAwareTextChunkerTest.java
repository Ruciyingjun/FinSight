/**
 * 边界感知切块器单元测试：验证最大长度、重叠内容、自然边界和页码隔离规则。
 */
package com.financialresearch.document.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.financialresearch.document.parser.ParsedPage;

class BoundaryAwareTextChunkerTest {

    private final BoundaryAwareTextChunker chunker =
            new BoundaryAwareTextChunker(new ChunkingProperties(100, 20));

    @Test
    void splitsAtNaturalBoundaryAndKeepsOverlap() {
        String firstParagraph = "A".repeat(65);
        String secondParagraph = "B".repeat(65);
        String text = firstParagraph + "\n\n" + secondParagraph;

        List<TextChunk> chunks = chunker.chunk(List.of(new ParsedPage(3, text)));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst().content()).isEqualTo(firstParagraph);
        assertThat(chunks.getFirst().pageNumber()).isEqualTo(3);
        assertThat(chunks.getFirst().startOffset()).isZero();
        assertThat(chunks.getFirst().endOffset()).isEqualTo(65);
        assertThat(chunks.getLast().content()).startsWith("A").endsWith(secondParagraph);
        assertThat(chunks.getLast().startOffset()).isEqualTo(47);
        assertThat(chunks.getLast().content().length()).isLessThanOrEqualTo(100);
    }

    @Test
    void neverCombinesDifferentPages() {
        List<TextChunk> chunks = chunker.chunk(List.of(
                new ParsedPage(1, "first"),
                new ParsedPage(2, "second")));

        assertThat(chunks).extracting(TextChunk::pageNumber).containsExactly(1, 2);
        assertThat(chunks).extracting(TextChunk::chunkIndex).containsExactly(0, 1);
    }
}
