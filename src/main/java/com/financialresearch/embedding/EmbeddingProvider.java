/**
 * 向量生成接口：隔离本地测试向量与未来接入的真实 Embedding 服务。
 */
package com.financialresearch.embedding;

public interface EmbeddingProvider {

    EmbeddingVector embed(String text);
}
