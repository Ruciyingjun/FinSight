/**
 * 问答编排服务：执行检索、生成引用编号、调用答案生成器并组装最终响应。
 */
package com.financialresearch.question;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.financialresearch.document.DocumentService;
import com.financialresearch.document.chunk.DocumentChunkResponse;
import com.financialresearch.document.chunk.DocumentChunkService;
import com.financialresearch.retrieval.RetrievalResult;
import com.financialresearch.retrieval.RetrievalProperties;
import com.financialresearch.retrieval.RetrievalReranker;
import com.financialresearch.retrieval.SearchService;

@Service
public class QuestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionService.class);

    private final SearchService searchService;
    private final AnswerGenerator answerGenerator;
    private final RetrievalReranker reranker;
    private final RetrievalProperties retrievalProperties;
    private final QuestionContextAssembler contextAssembler;
    private final AgenticQueryRouter queryRouter;
    private final DocumentService documentService;
    private final DocumentChunkService chunkService;

    public QuestionService(SearchService searchService, AnswerGenerator answerGenerator,
            RetrievalReranker reranker, RetrievalProperties retrievalProperties,
            QuestionContextAssembler contextAssembler, AgenticQueryRouter queryRouter,
            DocumentService documentService, DocumentChunkService chunkService) {
        this.searchService = searchService;
        this.answerGenerator = answerGenerator;
        this.reranker = reranker;
        this.retrievalProperties = retrievalProperties;
        this.contextAssembler = contextAssembler;
        this.queryRouter = queryRouter;
        this.documentService = documentService;
        this.chunkService = chunkService;
    }

    public QuestionResponse ask(QuestionRequest request) {
        PreparedQuestion prepared = prepare(request);
        GeneratedAnswer answer = prepared.hasDirectAnswer()
                ? prepared.directAnswer()
                : answerGenerator.generate(prepared.context());
        return response(prepared, answer);
    }

    PreparedQuestion prepare(QuestionRequest request) {
        String question = request.question().strip();
        String selectedDocumentName = selectedDocumentName(request.documentId());
        QueryPlan plan = queryRouter.plan(question, selectedDocumentName);
        LOGGER.info("问答规划: action={}, strategy={}, document={}, retrievalQuery={}",
                plan.action(), plan.retrievalStrategy(), selectedDocumentName, plan.retrievalQuery());
        if (plan.action() != QueryAction.RETRIEVE) {
            return PreparedQuestion.direct(question, plan.response());
        }
        int candidateLimit = Math.min(50, Math.max(
                retrievalProperties.minimumCandidates(),
                request.effectiveLimit() * retrievalProperties.candidateMultiplier()));
        List<RetrievalResult> candidates = searchService.search(
                plan.retrievalQuery(), request.documentId(), candidateLimit);
        List<RetrievalResult> strategyCandidates = plan.retrievalStrategy() == RetrievalStrategy.DOCUMENT_SUMMARY
                ? addRepresentativeChunks(candidates, request.documentId(), request.effectiveLimit())
                : candidates;
        List<RetrievalResult> retrievalResults = plan.retrievalStrategy() == RetrievalStrategy.DOCUMENT_SUMMARY
                ? reranker.selectSummaryEvidence(strategyCandidates, request.effectiveLimit())
                : reranker.rerank(plan.retrievalQuery(), candidates, request.effectiveLimit());
        LOGGER.info("问答检索: candidates={}, accepted={}, pages={}", candidates.size(),
                retrievalResults.size(), retrievalResults.stream().map(RetrievalResult::pageNumber).toList());
        List<CitationResponse> citations = citations(retrievalResults);
        List<ContextPassage> passages = contextAssembler.assemble(citations);
        return PreparedQuestion.retrieval(question, citations, passages, selectedDocumentName);
    }

    private String selectedDocumentName(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return null;
        }
        return documentService.get(documentId).filename();
    }

    private List<RetrievalResult> addRepresentativeChunks(
            List<RetrievalResult> candidates, String documentId, int limit) {
        if (documentId == null || documentId.isBlank()) {
            return candidates;
        }
        List<DocumentChunkResponse> chunks = chunkService.list(documentId);
        if (chunks.isEmpty()) {
            return candidates;
        }
        List<RetrievalResult> augmented = new ArrayList<>(candidates);
        int sampleCount = Math.min(limit, chunks.size());
        for (int index = 0; index < sampleCount; index++) {
            int chunkIndex = sampleCount == 1 ? 0
                    : (int) Math.round((double) index * (chunks.size() - 1) / (sampleCount - 1));
            DocumentChunkResponse chunk = chunks.get(chunkIndex);
            boolean alreadyPresent = augmented.stream().anyMatch(candidate ->
                    candidate.documentId().equals(chunk.documentId())
                            && candidate.chunkIndex() == chunk.chunkIndex());
            if (!alreadyPresent) {
                augmented.add(new RetrievalResult(
                        chunk.documentId(), chunk.chunkIndex(), chunk.pageNumber(),
                        chunk.startOffset(), chunk.endOffset(), chunk.content(), 0));
            }
        }
        return List.copyOf(augmented);
    }

    QuestionResponse generateStreaming(PreparedQuestion prepared, Consumer<String> tokenConsumer) {
        GeneratedAnswer answer;
        if (prepared.hasDirectAnswer()) {
            answer = prepared.directAnswer();
            tokenConsumer.accept(answer.text());
        } else {
            answer = answerGenerator.generateStreaming(prepared.context(), tokenConsumer);
        }
        return response(prepared, answer);
    }

    private QuestionResponse response(PreparedQuestion prepared, GeneratedAnswer answer) {
        return new QuestionResponse(prepared.question(), answer.text(), answer.source(),
                !prepared.citations().isEmpty(), prepared.citations());
    }

    private List<CitationResponse> citations(List<RetrievalResult> results) {
        List<CitationResponse> citations = new ArrayList<>(results.size());
        for (int index = 0; index < results.size(); index++) {
            RetrievalResult result = results.get(index);
            citations.add(new CitationResponse(
                    index + 1,
                    result.documentId(),
                    result.pageNumber(),
                    result.chunkIndex(),
                    result.startOffset(),
                    result.endOffset(),
                    result.content(),
                    result.score()));
        }
        return List.copyOf(citations);
    }
}
