/**
 * 文档 API 响应模型：向客户端公开安全且稳定的文档元数据。
 */
package com.financialresearch.document;

import java.time.Instant;

public record DocumentResponse(
        String id,
        String filename,
        String contentType,
        long sizeBytes,
        String sha256,
        DocumentStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    static DocumentResponse from(DocumentEntity document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getSha256(),
                document.getStatus(),
                document.getFailureReason(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
