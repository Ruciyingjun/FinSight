/**
 * 大模型客户端接口：隔离具体模型供应商的网络协议和响应格式。
 */
package com.financialresearch.question;

import java.util.function.Consumer;

public interface LlmClient {

    String generate(AnswerContext context);

    default QueryPlan plan(String question, String selectedDocumentName) {
        return QueryPlan.retrieve(question);
    }

    default String generateStreaming(AnswerContext context, Consumer<String> tokenConsumer) {
        String answer = generate(context);
        tokenConsumer.accept(answer);
        return answer;
    }
}
