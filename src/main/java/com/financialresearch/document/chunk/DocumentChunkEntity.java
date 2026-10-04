/**
 * 文档切块实体：持久化检索文本、块序号、所属页码及页内字符范围。
 */
package com.financialresearch.document.chunk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "document_chunks", uniqueConstraints = @UniqueConstraint(
        name = "uk_document_chunks_index", columnNames = {"document_id", "chunk_index"}))
class DocumentChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", length = 36, nullable = false)
    private String documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    @Column(name = "end_offset", nullable = false)
    private int endOffset;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    protected DocumentChunkEntity() {
    }

    DocumentChunkEntity(String documentId, TextChunk chunk, String embeddingModel, float[] embedding) {
        this.documentId = documentId;
        this.chunkIndex = chunk.chunkIndex();
        this.content = chunk.content();
        this.pageNumber = chunk.pageNumber();
        this.startOffset = chunk.startOffset();
        this.endOffset = chunk.endOffset();
        this.characterCount = chunk.content().length();
        this.embedding = EmbeddingCodec.encode(embedding);
        this.embeddingDimension = embedding.length;
        this.embeddingModel = embeddingModel;
    }

    String getDocumentId() { return documentId; }
    int getChunkIndex() { return chunkIndex; }
    String getContent() { return content; }
    int getPageNumber() { return pageNumber; }
    int getStartOffset() { return startOffset; }
    int getEndOffset() { return endOffset; }
    int getCharacterCount() { return characterCount; }
    String getEmbedding() { return embedding; }
    Integer getEmbeddingDimension() { return embeddingDimension; }
    String getEmbeddingModel() { return embeddingModel; }
}
