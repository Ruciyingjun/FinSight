/**
 * 答案生成器接口：隔离抽取式实现与未来接入的大语言模型实现。
 */
package com.financialresearch.question;

import java.util.function.Consumer;

public interface AnswerGenerator {

    GeneratedAnswer generate(AnswerContext context);

    default GeneratedAnswer generateStreaming(AnswerContext context, Consumer<String> tokenConsumer) {
        GeneratedAnswer answer = generate(context);
        tokenConsumer.accept(answer.text());
        return answer;
    }
}
