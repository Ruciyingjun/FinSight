/**
 * 边界感知切块器：优先在段落、换行、句末或空格处断开，并为相邻块保留重叠文本。
 */
package com.financialresearch.document.chunk;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.financialresearch.document.parser.ParsedPage;

@Component
public class BoundaryAwareTextChunker implements TextChunker {

    private static final double MIN_BOUNDARY_RATIO = 0.6;

    private final int maxCharacters;
    private final int overlapCharacters;

    public BoundaryAwareTextChunker(ChunkingProperties properties) {
        this.maxCharacters = properties.maxCharacters();
        this.overlapCharacters = properties.overlapCharacters();
    }

    @Override
    public List<TextChunk> chunk(List<ParsedPage> pages) {
        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (ParsedPage page : pages) {
            String text = page.content();
            int start = skipWhitespace(text, 0);
            while (start < text.length()) {
                int end = chooseEnd(text, start);
                int contentStart = start;
                int contentEnd = trimEnd(text, end);
                if (contentEnd > contentStart) {
                    chunks.add(new TextChunk(
                            chunkIndex++, page.pageNumber(), contentStart, contentEnd,
                            text.substring(contentStart, contentEnd)));
                }
                if (end >= text.length()) {
                    break;
                }
                int next = Math.max(start + 1, end - overlapCharacters);
                start = skipWhitespace(text, next);
            }
        }
        return List.copyOf(chunks);
    }

    private int chooseEnd(String text, int start) {
        int hardEnd = Math.min(text.length(), start + maxCharacters);
        if (hardEnd == text.length()) {
            return hardEnd;
        }
        int minimum = start + (int) (maxCharacters * MIN_BOUNDARY_RATIO);
        int boundary = lastBoundary(text, "\n\n", minimum, hardEnd);
        if (boundary < 0) {
            boundary = lastBoundary(text, "\n", minimum, hardEnd);
        }
        if (boundary < 0) {
            boundary = lastSentenceBoundary(text, minimum, hardEnd);
        }
        if (boundary < 0) {
            boundary = lastBoundary(text, " ", minimum, hardEnd);
        }
        return boundary < 0 ? hardEnd : boundary;
    }

    private int lastBoundary(String text, String delimiter, int minimum, int hardEnd) {
        int position = text.lastIndexOf(delimiter, hardEnd - 1);
        return position >= minimum ? position + delimiter.length() : -1;
    }

    private int lastSentenceBoundary(String text, int minimum, int hardEnd) {
        for (int index = hardEnd - 1; index >= minimum; index--) {
            char current = text.charAt(index);
            if (current == '。' || current == '！' || current == '？'
                    || current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        return -1;
    }

    private int skipWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private int trimEnd(String text, int end) {
        int index = end;
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }
}
