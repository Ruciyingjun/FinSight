/**
 * 文档持久化实体：保存原始文件信息、存储标识、摘要和处理状态。
 */
package com.financialresearch.document;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
class DocumentEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "original_filename", length = 512, nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", length = 128, nullable = false, unique = true)
    private String storedFilename;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(length = 64, nullable = false)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private DocumentStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentEntity() {
    }

    DocumentEntity(String id, String originalFilename, String storedFilename, String contentType,
            long sizeBytes, String sha256, Instant now) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.status = DocumentStatus.UPLOADED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    String getId() { return id; }
    String getOriginalFilename() { return originalFilename; }
    String getStoredFilename() { return storedFilename; }
    String getContentType() { return contentType; }
    long getSizeBytes() { return sizeBytes; }
    String getSha256() { return sha256; }
    DocumentStatus getStatus() { return status; }
    String getFailureReason() { return failureReason; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }

    void queue(Instant now) {
        if (status != DocumentStatus.UPLOADED && status != DocumentStatus.FAILED) {
            throw new InvalidDocumentStateException(id, status, DocumentStatus.QUEUED);
        }
        status = DocumentStatus.QUEUED;
        failureReason = null;
        updatedAt = now;
    }

    void requeueInterrupted(Instant now) {
        if (status != DocumentStatus.QUEUED && status != DocumentStatus.PARSING
                && status != DocumentStatus.CHUNKING && status != DocumentStatus.INDEXING) {
            throw new InvalidDocumentStateException(id, status, DocumentStatus.QUEUED);
        }
        status = DocumentStatus.QUEUED;
        failureReason = null;
        updatedAt = now;
    }

    void startParsing(Instant now) {
        if (status != DocumentStatus.QUEUED) {
            throw new InvalidDocumentStateException(id, status, DocumentStatus.PARSING);
        }
        status = DocumentStatus.PARSING;
        failureReason = null;
        updatedAt = now;
    }

    void finishParsing(Instant now) {
        if (status != DocumentStatus.PARSING) {
            throw new InvalidDocumentStateException(id, status, DocumentStatus.CHUNKING);
        }
        status = DocumentStatus.CHUNKING;
        updatedAt = now;
    }

    void finishChunking(Instant now) {
        if (status != DocumentStatus.CHUNKING) {
            throw new InvalidDocumentStateException(id, status, DocumentStatus.INDEXING);
        }
        status = DocumentStatus.INDEXING;
        updatedAt = now;
    }

    void finishIndexing(Instant now) {
        if (status != DocumentStatus.INDEXING) {
            throw new InvalidDocumentStateException(id, status, DocumentStatus.READY);
        }
        status = DocumentStatus.READY;
        updatedAt = now;
    }

    void fail(String reason, Instant now) {
        status = DocumentStatus.FAILED;
        failureReason = reason == null ? "文档处理失败" : reason.substring(0, Math.min(reason.length(), 1000));
        updatedAt = now;
    }
}
