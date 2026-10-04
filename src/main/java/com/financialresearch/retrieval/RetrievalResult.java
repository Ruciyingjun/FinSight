/**
 * 检索结果模型：返回匹配文本、BM25 分数以及可定位到原文的页码和字符范围。
 */
package com.financialresearch.retrieval;

public record RetrievalResult(
        String documentId,
        int chunkIndex,
        int pageNumber,
        int startOffset,
        int endOffset,
        String content,
        double score) {
}
