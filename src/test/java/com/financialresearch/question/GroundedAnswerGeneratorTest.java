/**
 * 大模型答案生成测试：验证合法引用答案被采用，非法引用和调用异常会安全回退。
 */
package com.financialresearch.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class GroundedAnswerGeneratorTest {

    private final ExtractiveAnswerGenerator fallback = new ExtractiveAnswerGenerator();
    private final LlmProperties enabled = new LlmProperties(
            true, "http://localhost/unused", "key", "model", 5, 200, 0.1);

    @Test
    void usesModelAnswerWithValidCitation() {
        GroundedAnswerGenerator generator = new GroundedAnswerGenerator(
                enabled, context -> "医疗保障属于保险责任。[1]", fallback);

        GeneratedAnswer answer = generator.generate(context());
        assertThat(answer.text()).isEqualTo("医疗保障属于保险责任。[1]");
        assertThat(answer.source()).isEqualTo(AnswerSource.LARGE_LANGUAGE_MODEL);
    }

    @Test
    void fallsBackWhenModelInventsCitation() {
        GroundedAnswerGenerator generator = new GroundedAnswerGenerator(
                enabled, context -> "模型编造的结论。[9]", fallback);

        GeneratedAnswer answer = generator.generate(context());
        assertThat(answer.text()).isEqualTo("[1] 保险责任包括医疗保障");
        assertThat(answer.source()).isEqualTo(AnswerSource.LOCAL_FALLBACK);
    }

    @Test
    void fallsBackWhenModelRequestFails() {
        GroundedAnswerGenerator generator = new GroundedAnswerGenerator(enabled, context -> {
            throw new LlmGenerationException("连接失败");
        }, fallback);

        GeneratedAnswer answer = generator.generate(context());
        assertThat(answer.text()).isEqualTo("[1] 保险责任包括医疗保障");
        assertThat(answer.source()).isEqualTo(AnswerSource.LOCAL_FALLBACK);
    }

    @Test
    void streamsModelOutputAndKeepsValidatedFinalAnswer() {
        GroundedAnswerGenerator generator = new GroundedAnswerGenerator(
                enabled, context -> "医疗保障属于保险责任。[1]", fallback);
        List<String> tokens = new ArrayList<>();

        GeneratedAnswer answer = generator.generateStreaming(context(), tokens::add);

        assertThat(tokens).containsExactly("医疗保障属于保险责任。[1]");
        assertThat(answer.text()).isEqualTo("医疗保障属于保险责任。[1]");
        assertThat(answer.source()).isEqualTo(AnswerSource.LARGE_LANGUAGE_MODEL);
    }

    @Test
    void replacesInvalidStreamWithFallbackFinalAnswer() {
        GroundedAnswerGenerator generator = new GroundedAnswerGenerator(
                enabled, context -> "错误结论。[9]", fallback);
        List<String> tokens = new ArrayList<>();

        GeneratedAnswer answer = generator.generateStreaming(context(), tokens::add);

        assertThat(tokens).containsExactly("错误结论。[9]");
        assertThat(answer.text()).isEqualTo("[1] 保险责任包括医疗保障");
        assertThat(answer.source()).isEqualTo(AnswerSource.LOCAL_FALLBACK);
    }

    private AnswerContext context() {
        CitationResponse citation = new CitationResponse(
                1, "document", 1, 0, 0, 10, "保险责任包括医疗保障", 1.0);
        ContextPassage passage = new ContextPassage(List.of(1), "保险责任包括医疗保障");
        return new AnswerContext("有哪些保险责任？", List.of(citation), List.of(passage));
    }
}
