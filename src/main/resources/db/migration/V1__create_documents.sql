-- 初始数据库迁移：创建文档元数据表及状态、创建时间索引。
CREATE TABLE documents (
    id VARCHAR(36) PRIMARY KEY,
    original_filename VARCHAR(512) NOT NULL,
    stored_filename VARCHAR(128) NOT NULL UNIQUE,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_documents_created_at ON documents (created_at);
CREATE INDEX idx_documents_status ON documents (status);
