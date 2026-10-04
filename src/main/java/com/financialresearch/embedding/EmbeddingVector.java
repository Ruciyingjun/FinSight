/**
 * 向量结果模型：保存模型标识及其生成的归一化浮点向量。
 */
package com.financialresearch.embedding;

public record EmbeddingVector(String model, float[] values) {

    public EmbeddingVector {
        values = values.clone();
    }

    @Override
    public float[] values() {
        return values.clone();
    }
}
