/**
 * 问答引用模型：将答案中的编号映射到具体文档、页码、文本块和原文片段。
 */
package com.financialresearch.question;

public record CitationResponse(
        int number,
        String documentId,
        int pageNumber,
        int chunkIndex,
        int startOffset,
        int endOffset,
        String quote,
        double score) {
}
