/**
 * 文档处理状态事务服务：让排队、解析、切块、索引和失败状态即时提交并可被前端查询。
 */
package com.financialresearch.document;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.financialresearch.document.parser.ParsedPage;

@Service
class DocumentProcessingStateService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;

    DocumentProcessingStateService(DocumentRepository documentRepository, DocumentPageRepository pageRepository) {
        this.documentRepository = documentRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional
    DocumentResponse queue(String id) {
        DocumentEntity document = find(id);
        document.queue(Instant.now());
        return DocumentResponse.from(documentRepository.save(document));
    }

    @Transactional
    void requeueInterrupted(String id) {
        DocumentEntity document = find(id);
        document.requeueInterrupted(Instant.now());
        documentRepository.save(document);
    }

    @Transactional
    DocumentEntity startParsing(String id) {
        DocumentEntity document = find(id);
        document.startParsing(Instant.now());
        return documentRepository.save(document);
    }

    @Transactional
    void replacePagesAndStartChunking(String id, List<ParsedPage> pages) {
        pageRepository.deleteByDocumentId(id);
        pageRepository.flush();
        pageRepository.saveAll(pages.stream()
                .map(page -> new DocumentPageEntity(id, page.pageNumber(), page.content()))
                .toList());
        DocumentEntity document = find(id);
        document.finishParsing(Instant.now());
        documentRepository.save(document);
    }

    @Transactional
    void startIndexing(String id) {
        DocumentEntity document = find(id);
        document.finishChunking(Instant.now());
        documentRepository.save(document);
    }

    @Transactional
    void finish(String id) {
        DocumentEntity document = find(id);
        document.finishIndexing(Instant.now());
        documentRepository.save(document);
    }

    @Transactional
    void fail(String id, String reason) {
        documentRepository.findById(id).ifPresent(document -> {
            document.fail(reason, Instant.now());
            documentRepository.save(document);
        });
    }

    private DocumentEntity find(String id) {
        return documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }
}
