/**
 * 文档处理服务：选择合适解析器、推进处理状态并持久化逐页文本。
 */
package com.financialresearch.document;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.financialresearch.document.parser.DocumentParser;
import com.financialresearch.document.parser.ParsedPage;
import com.financialresearch.document.storage.DocumentStorage;
import com.financialresearch.document.chunk.DocumentChunkService;

@Service
public class DocumentProcessingService {

    private final DocumentPageRepository pageRepository;
    private final DocumentStorage storage;
    private final List<DocumentParser> parsers;
    private final DocumentChunkService chunkService;
    private final DocumentProcessingStateService stateService;
    private final Executor executor;

    public DocumentProcessingService(DocumentPageRepository pageRepository,
            DocumentStorage storage,
            List<DocumentParser> parsers,
            DocumentChunkService chunkService,
            DocumentProcessingStateService stateService,
            @Qualifier("documentProcessingExecutor") Executor executor) {
        this.pageRepository = pageRepository;
        this.storage = storage;
        this.parsers = parsers;
        this.chunkService = chunkService;
        this.stateService = stateService;
        this.executor = executor;
    }

    public DocumentResponse enqueue(String id) {
        DocumentResponse queued = stateService.queue(id);
        try {
            executor.execute(() -> processInBackground(id));
        } catch (RejectedExecutionException exception) {
            stateService.fail(id, "后台处理队列已满，请稍后重试");
            throw new DocumentProcessingQueueFullException();
        }
        return queued;
    }

    void resumeInterrupted(String id) {
        stateService.requeueInterrupted(id);
        try {
            executor.execute(() -> processInBackground(id));
        } catch (RejectedExecutionException exception) {
            stateService.fail(id, "服务重启后恢复任务失败：后台处理队列已满，请手动重试");
        }
    }

    private void processInBackground(String id) {
        try {
            DocumentEntity document = stateService.startParsing(id);
            DocumentParser parser = parsers.stream()
                    .filter(candidate -> candidate.supports(document.getOriginalFilename()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidDocumentException(
                            "尚未提供该文件类型的解析器: " + document.getOriginalFilename()));

            List<ParsedPage> parsedPages;
            try (InputStream input = storage.open(document.getStoredFilename())) {
                parsedPages = parser.parse(input);
            }

            stateService.replacePagesAndStartChunking(id, parsedPages);
            chunkService.replaceChunks(id, parsedPages);
            stateService.startIndexing(id);
            stateService.finish(id);
        } catch (Exception exception) {
            stateService.fail(id, rootMessage(exception));
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<DocumentPageResponse> pages(String documentId) {
        return pageRepository.findByDocumentIdOrderByPageNumber(documentId).stream()
                .map(DocumentPageResponse::from)
                .toList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public DocumentPageResponse page(String documentId, int pageNumber) {
        return pageRepository.findByDocumentIdAndPageNumber(documentId, pageNumber)
                .map(DocumentPageResponse::from)
                .orElseThrow(() -> new InvalidDocumentException(
                        "文档第 " + pageNumber + " 页不存在或尚未完成解析"));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<com.financialresearch.document.chunk.DocumentChunkResponse> chunks(String documentId) {
        return chunkService.list(documentId);
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? exception.getClass().getSimpleName() : current.getMessage();
    }
}
