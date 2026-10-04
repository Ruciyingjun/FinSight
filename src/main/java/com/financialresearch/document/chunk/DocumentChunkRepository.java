/**
 * 文档切块仓库：按文档和块序号读取或替换持久化的检索文本块。
 */
package com.financialresearch.document.chunk;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, Long> {

    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(String documentId);

    void deleteByDocumentId(String documentId);
}
