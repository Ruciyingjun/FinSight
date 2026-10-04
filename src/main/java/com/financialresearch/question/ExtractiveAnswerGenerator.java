/**
 * 抽取式答案生成器：从最高相关证据中提取短片段并生成带引用编号的确定性答案。
 */
package com.financialresearch.question;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
class ExtractiveAnswerGenerator implements AnswerGenerator {

    private static final int MAX_QUOTE_CHARACTERS = 300;

    @Override
    public GeneratedAnswer generate(AnswerContext context) {
        if (context.citations().isEmpty()) {
            return localAnswer("未在当前资料库中找到与该问题相关的内容。");
        }
        String text = context.passages().stream()
                .limit(3)
                .map(passage -> citationLabel(passage) + " " + shorten(passage.content()))
                .collect(Collectors.joining("\n"));
        return localAnswer(text);
    }

    private GeneratedAnswer localAnswer(String text) {
        return new GeneratedAnswer(text, AnswerSource.LOCAL_FALLBACK);
    }

    private String citationLabel(ContextPassage passage) {
        return passage.citationNumbers().stream()
                .map(number -> "[" + number + "]")
                .collect(Collectors.joining());
    }

    private String shorten(String text) {
        String normalized = text.replaceAll("\\s+", " ").strip();
        if (normalized.length() <= MAX_QUOTE_CHARACTERS) {
            return normalized;
        }
        int sentenceEnd = lastSentenceEnd(normalized, MAX_QUOTE_CHARACTERS);
        int end = sentenceEnd > MAX_QUOTE_CHARACTERS / 2 ? sentenceEnd : MAX_QUOTE_CHARACTERS;
        return normalized.substring(0, end).strip() + "…";
    }

    private int lastSentenceEnd(String text, int maximum) {
        for (int index = Math.min(maximum, text.length()) - 1; index >= 0; index--) {
            char current = text.charAt(index);
            if (current == '。' || current == '！' || current == '？'
                    || current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        return -1;
    }
}
