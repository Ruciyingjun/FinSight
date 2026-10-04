/**
 * 文档数据仓库：提供文档元数据的持久化和按创建时间查询能力。
 */
package com.financialresearch.document;

import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    List<DocumentEntity> findAllByOrderByCreatedAtDesc();

    List<DocumentEntity> findByStatusIn(Collection<DocumentStatus> statuses);
}
