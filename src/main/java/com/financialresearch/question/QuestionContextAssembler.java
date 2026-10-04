/**
 * 问答上下文组装器：合并同页相邻证据、清除切块重叠，并限制发送给答案生成器的总字符数。
 */
package com.financialresearch.question;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.financialresearch.retrieval.RetrievalProperties;

@Component
public class QuestionContextAssembler {

    private final RetrievalProperties properties;

    public QuestionContextAssembler(RetrievalProperties properties) {
        this.properties = properties;
    }

    public List<ContextPassage> assemble(List<CitationResponse> citations) {
        List<ContextPassage> passages = new ArrayList<>();
        CitationResponse previousCitation = null;
        int usedCharacters = 0;
        for (CitationResponse citation : citations) {
            if (usedCharacters >= properties.maximumContextCharacters()) {
                break;
            }
            String content = citation.quote().strip();
            if (!passages.isEmpty() && canMerge(previousCitation, citation)) {
                ContextPassage previous = passages.getLast();
                String merged = mergeOverlap(previous.content(), content);
                merged = truncate(merged, properties.maximumContextCharacters()
                        - usedCharacters + previous.content().length());
                List<Integer> numbers = new ArrayList<>(previous.citationNumbers());
                numbers.add(citation.number());
                passages.set(passages.size() - 1, new ContextPassage(List.copyOf(numbers), merged));
                usedCharacters += merged.length() - previous.content().length();
                previousCitation = citation;
                continue;
            }
            String accepted = truncate(content, properties.maximumContextCharacters() - usedCharacters);
            if (!accepted.isBlank()) {
                passages.add(new ContextPassage(List.of(citation.number()), accepted));
                usedCharacters += accepted.length();
                previousCitation = citation;
            }
        }
        return List.copyOf(passages);
    }

    private boolean canMerge(CitationResponse previous, CitationResponse current) {
        return previous != null
                && previous.documentId().equals(current.documentId())
                && previous.pageNumber() == current.pageNumber()
                && current.chunkIndex() == previous.chunkIndex() + 1
                && current.startOffset() <= previous.endOffset();
    }

    private String mergeOverlap(String first, String second) {
        int maximum = Math.min(first.length(), second.length());
        for (int overlap = maximum; overlap > 0; overlap--) {
            if (first.regionMatches(first.length() - overlap, second, 0, overlap)) {
                return first + second.substring(overlap);
            }
        }
        return first + "\n" + second;
    }

    private String truncate(String text, int maximum) {
        return text.length() <= maximum ? text : text.substring(0, Math.max(0, maximum));
    }
}
