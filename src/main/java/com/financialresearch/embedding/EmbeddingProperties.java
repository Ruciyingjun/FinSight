/**
 * 向量配置：定义本地或外部 Embedding 模型生成向量时使用的维度和模型标识。
 */
package com.financialresearch.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.embedding")
public record EmbeddingProperties(int dimensions, String model) {

    public EmbeddingProperties {
        if (dimensions < 64 || dimensions > 4096) {
            throw new IllegalArgumentException("embedding dimensions 必须在 64 到 4096 之间");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("embedding model 不能为空");
        }
    }
}
