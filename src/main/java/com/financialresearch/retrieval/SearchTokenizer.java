/**
 * 检索分词器：提取英文数字词，并为连续中文生成单字和双字词元以支持混合语言搜索。
 */
package com.financialresearch.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class SearchTokenizer {

    public List<String> tokenize(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        StringBuilder han = new StringBuilder();

        normalized.codePoints().forEach(codePoint -> {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushLatin(latin, tokens);
                han.appendCodePoint(codePoint);
            } else if (Character.isLetterOrDigit(codePoint)) {
                flushHan(han, tokens);
                latin.appendCodePoint(codePoint);
            } else {
                flushLatin(latin, tokens);
                flushHan(han, tokens);
            }
        });
        flushLatin(latin, tokens);
        flushHan(han, tokens);
        return List.copyOf(tokens);
    }

    private void flushLatin(StringBuilder buffer, List<String> tokens) {
        if (!buffer.isEmpty()) {
            tokens.add(buffer.toString());
            buffer.setLength(0);
        }
    }

    private void flushHan(StringBuilder buffer, List<String> tokens) {
        if (buffer.isEmpty()) {
            return;
        }
        int[] characters = buffer.codePoints().toArray();
        for (int character : characters) {
            tokens.add(new String(Character.toChars(character)));
        }
        for (int index = 0; index + 1 < characters.length; index++) {
            tokens.add(new String(characters, index, 2));
        }
        buffer.setLength(0);
    }
}
