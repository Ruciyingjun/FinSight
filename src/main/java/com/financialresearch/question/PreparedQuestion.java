/**
 * 已准备问答模型：保存检索、重排和上下文组装后的问题、引用及模型输入。
 */
package com.financialresearch.question;

import java.util.List;

record PreparedQuestion(
        String question,
        List<CitationResponse> citations,
        AnswerContext context,
        GeneratedAnswer directAnswer) {

    static PreparedQuestion direct(String question, String answer) {
        List<CitationResponse> citations = List.of();
        return new PreparedQuestion(question, citations,
                new AnswerContext(question, null, citations, List.of()),
                new GeneratedAnswer(answer, AnswerSource.AGENT_RESPONSE));
    }

    static PreparedQuestion retrieval(String question, List<CitationResponse> citations,
            List<ContextPassage> passages, String selectedDocumentName) {
        return new PreparedQuestion(question, citations,
                new AnswerContext(question, selectedDocumentName, citations, passages), null);
    }

    boolean hasDirectAnswer() {
        return directAnswer != null;
    }
}
