/**
 * 内存文本块模型：表示切块算法输出的页码、页内位置和块内容。
 */
package com.financialresearch.document.chunk;

public record TextChunk(
        int chunkIndex,
        int pageNumber,
        int startOffset,
        int endOffset,
        String content) {
}
