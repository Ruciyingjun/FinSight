/**
 * 文档切块服务：运行切块算法、原子替换文档切块并提供有序查询。
 */
package com.financialresearch.document.chunk;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.financialresearch.document.parser.ParsedPage;
import com.financialresearch.embedding.EmbeddingProvider;
import com.financialresearch.embedding.EmbeddingVector;

@Service
public class DocumentChunkService {

    private final DocumentChunkRepository repository;
    private final TextChunker chunker;
    private final EmbeddingProvider embeddingProvider;

    public DocumentChunkService(DocumentChunkRepository repository, TextChunker chunker,
            EmbeddingProvider embeddingProvider) {
        this.repository = repository;
        this.chunker = chunker;
        this.embeddingProvider = embeddingProvider;
    }

    @Transactional
    public void replaceChunks(String documentId, List<ParsedPage> pages) {
        repository.deleteByDocumentId(documentId);
        repository.flush();
        repository.saveAll(chunker.chunk(pages).stream()
                .map(chunk -> toEntity(documentId, chunk))
                .toList());
    }

    public List<DocumentChunkResponse> list(String documentId) {
        return repository.findByDocumentIdOrderByChunkIndex(documentId).stream()
                .map(DocumentChunkResponse::from)
                .toList();
    }

    public List<DocumentChunkResponse> listAll() {
        return repository.findAll().stream()
                .map(DocumentChunkResponse::from)
                .toList();
    }

    public List<EmbeddedDocumentChunk> listEmbedded(String documentId) {
        List<DocumentChunkEntity> chunks = documentId == null || documentId.isBlank()
                ? repository.findAll()
                : repository.findByDocumentIdOrderByChunkIndex(documentId);
        return chunks.stream()
                .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbeddingDimension() != null)
                .map(this::toEmbeddedChunk)
                .toList();
    }

    private DocumentChunkEntity toEntity(String documentId, TextChunk chunk) {
        EmbeddingVector vector = embeddingProvider.embed(chunk.content());
        return new DocumentChunkEntity(documentId, chunk, vector.model(), vector.values());
    }

    private EmbeddedDocumentChunk toEmbeddedChunk(DocumentChunkEntity chunk) {
        return new EmbeddedDocumentChunk(
                chunk.getDocumentId(), chunk.getChunkIndex(), chunk.getContent(), chunk.getPageNumber(),
                chunk.getStartOffset(), chunk.getEndOffset(), chunk.getEmbeddingModel(),
                EmbeddingCodec.decode(chunk.getEmbedding(), chunk.getEmbeddingDimension()));
    }
}
