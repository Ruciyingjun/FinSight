/**
 * 已向量化文本块模型：向检索层提供引用元数据、原文和内部向量，不暴露持久化实体。
 */
package com.financialresearch.document.chunk;

public record EmbeddedDocumentChunk(
        String documentId,
        int chunkIndex,
        String content,
        int pageNumber,
        int startOffset,
        int endOffset,
        String embeddingModel,
        float[] embedding) {

    public EmbeddedDocumentChunk {
        embedding = embedding.clone();
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }
}
