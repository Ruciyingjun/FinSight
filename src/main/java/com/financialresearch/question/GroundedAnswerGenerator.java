/**
 * 有依据的答案生成器：启用时调用大模型，并在失败或引用不合法时安全降级为抽取式答案。
 */
package com.financialresearch.question;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
class GroundedAnswerGenerator implements AnswerGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroundedAnswerGenerator.class);
    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");

    private final LlmProperties properties;
    private final LlmClient llmClient;
    private final ExtractiveAnswerGenerator fallback;

    GroundedAnswerGenerator(LlmProperties properties, LlmClient llmClient,
            ExtractiveAnswerGenerator fallback) {
        this.properties = properties;
        this.llmClient = llmClient;
        this.fallback = fallback;
    }

    @Override
    public GeneratedAnswer generate(AnswerContext context) {
        if (!properties.enabled() || context.citations().isEmpty()) {
            return fallback.generate(context);
        }
        try {
            String answer = llmClient.generate(context);
            if (!containsOnlyValidCitations(answer, context.citations().size())) {
                LOGGER.warn("大模型答案缺少合法引用，已使用抽取式答案回退");
                return fallback.generate(context);
            }
            return new GeneratedAnswer(answer, AnswerSource.LARGE_LANGUAGE_MODEL);
        } catch (RuntimeException exception) {
            LOGGER.warn("大模型生成失败，已使用抽取式答案回退: {}", exception.getMessage());
            return fallback.generate(context);
        }
    }

    @Override
    public GeneratedAnswer generateStreaming(AnswerContext context, Consumer<String> tokenConsumer) {
        if (!properties.enabled() || context.citations().isEmpty()) {
            return fallback.generateStreaming(context, tokenConsumer);
        }
        try {
            String answer = llmClient.generateStreaming(context, tokenConsumer);
            if (!containsOnlyValidCitations(answer, context.citations().size())) {
                LOGGER.warn("大模型流式答案缺少合法引用，最终答案已使用抽取式答案回退");
                return fallback.generate(context);
            }
            return new GeneratedAnswer(answer, AnswerSource.LARGE_LANGUAGE_MODEL);
        } catch (RuntimeException exception) {
            LOGGER.warn("大模型流式生成失败，最终答案已使用抽取式答案回退: {}", exception.getMessage());
            return fallback.generate(context);
        }
    }

    private boolean containsOnlyValidCitations(String answer, int citationCount) {
        Matcher matcher = CITATION.matcher(answer);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            int number = Integer.parseInt(matcher.group(1));
            if (number < 1 || number > citationCount) {
                return false;
            }
        }
        return found;
    }
}
