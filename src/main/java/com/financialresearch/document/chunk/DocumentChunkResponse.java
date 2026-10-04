/**
 * 文档切块响应模型：向客户端返回可检索文本及其原文定位信息。
 */
package com.financialresearch.document.chunk;

public record DocumentChunkResponse(
        String documentId,
        int chunkIndex,
        String content,
        int pageNumber,
        int startOffset,
        int endOffset,
        int characterCount) {

    static DocumentChunkResponse from(DocumentChunkEntity chunk) {
        return new DocumentChunkResponse(
                chunk.getDocumentId(), chunk.getChunkIndex(), chunk.getContent(), chunk.getPageNumber(),
                chunk.getStartOffset(), chunk.getEndOffset(), chunk.getCharacterCount());
    }
}
