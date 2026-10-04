/**
 * 文档页面响应模型：向客户端返回页码、页面文本和字符数量。
 */
package com.financialresearch.document;

public record DocumentPageResponse(
        String documentId,
        int pageNumber,
        String content,
        int characterCount) {

    static DocumentPageResponse from(DocumentPageEntity page) {
        return new DocumentPageResponse(
                page.getDocumentId(), page.getPageNumber(), page.getContent(), page.getCharacterCount());
    }
}
