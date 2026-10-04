-- 文本块向量迁移：为每个检索块保存可替换模型生成的向量、维度和模型标识。
ALTER TABLE document_chunks ADD COLUMN embedding TEXT;
ALTER TABLE document_chunks ADD COLUMN embedding_dimension INTEGER;
ALTER TABLE document_chunks ADD COLUMN embedding_model VARCHAR(128);
