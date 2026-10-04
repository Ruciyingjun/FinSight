/**
 * 文档页面实体：持久化解析后每一页的页码、原始文本和字符数量。
 */
package com.financialresearch.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "document_pages", uniqueConstraints = @UniqueConstraint(
        name = "uk_document_pages_number", columnNames = {"document_id", "page_number"}))
class DocumentPageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", length = 36, nullable = false)
    private String documentId;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    protected DocumentPageEntity() {
    }

    DocumentPageEntity(String documentId, int pageNumber, String content) {
        this.documentId = documentId;
        this.pageNumber = pageNumber;
        this.content = content;
        this.characterCount = content.length();
    }

    String getDocumentId() { return documentId; }
    int getPageNumber() { return pageNumber; }
    String getContent() { return content; }
    int getCharacterCount() { return characterCount; }
}
