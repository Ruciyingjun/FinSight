/**
 * 答案生成上下文：向答案生成器传递规范化问题及已编号的可靠引用证据。
 */
package com.financialresearch.question;

import java.util.List;

public record AnswerContext(
        String question,
        String selectedDocumentName,
        List<CitationResponse> citations,
        List<ContextPassage> passages) {

    public AnswerContext(String question, List<CitationResponse> citations, List<ContextPassage> passages) {
        this(question, null, citations, passages);
    }
}
