/**
 * 问答响应模型：返回原问题、抽取式答案、引用列表及是否找到有效证据。
 */
package com.financialresearch.question;

import java.util.List;

public record QuestionResponse(
        String question,
        String answer,
        AnswerSource answerSource,
        boolean evidenceFound,
        List<CitationResponse> citations) {
}
