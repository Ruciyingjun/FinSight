/**
 * 异步任务恢复器：应用启动后重新调度上次关闭时尚未完成的文档处理任务。
 */
package com.financialresearch.document;

import java.util.EnumSet;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class DocumentProcessingRecovery {

    private final DocumentRepository repository;
    private final DocumentProcessingService processingService;

    DocumentProcessingRecovery(DocumentRepository repository, DocumentProcessingService processingService) {
        this.repository = repository;
        this.processingService = processingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    void resumeInterruptedDocuments() {
        repository.findByStatusIn(EnumSet.of(
                        DocumentStatus.QUEUED,
                        DocumentStatus.PARSING,
                        DocumentStatus.CHUNKING,
                        DocumentStatus.INDEXING))
                .forEach(document -> processingService.resumeInterrupted(document.getId()));
    }
}
