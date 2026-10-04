/**
 * 文档页面仓库：按文档和页码顺序查询或清理解析后的页面文本。
 */
package com.financialresearch.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentPageRepository extends JpaRepository<DocumentPageEntity, Long> {

    List<DocumentPageEntity> findByDocumentIdOrderByPageNumber(String documentId);

    Optional<DocumentPageEntity> findByDocumentIdAndPageNumber(String documentId, int pageNumber);

    void deleteByDocumentId(String documentId);
}
