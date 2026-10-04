/**
 * 检索查询模型：封装查询文本、可选文档范围和最大返回数量。
 */
package com.financialresearch.retrieval;

public record RetrievalQuery(String text, String documentId, int limit) {

    public RetrievalQuery {
        if (text == null || text.isBlank()) {
            throw new InvalidSearchQueryException("查询内容不能为空");
        }
        text = text.strip();
        if (text.length() > 500) {
            throw new InvalidSearchQueryException("查询内容不能超过 500 个字符");
        }
        if (limit < 1 || limit > 50) {
            throw new InvalidSearchQueryException("limit 必须在 1 到 50 之间");
        }
    }
}
