/**
 * 本地哈希向量实现：将中英文词元映射为固定维度归一化向量，供离线开发和混合检索使用。
 */
package com.financialresearch.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.financialresearch.retrieval.SearchTokenizer;

@Component
class LocalHashingEmbeddingProvider implements EmbeddingProvider {

    private final SearchTokenizer tokenizer;
    private final EmbeddingProperties properties;

    LocalHashingEmbeddingProvider(SearchTokenizer tokenizer, EmbeddingProperties properties) {
        this.tokenizer = tokenizer;
        this.properties = properties;
    }

    @Override
    public EmbeddingVector embed(String text) {
        float[] vector = new float[properties.dimensions()];
        List<String> tokens = tokenizer.tokenize(text);
        for (String token : tokens) {
            byte[] digest = digest(token);
            int index = Math.floorMod(readInt(digest, 0), vector.length);
            float sign = (digest[4] & 1) == 0 ? 1.0f : -1.0f;
            vector[index] += sign;
        }
        normalize(vector);
        return new EmbeddingVector(properties.model(), vector);
    }

    private byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | (bytes[offset + 3] & 0xff);
    }

    private void normalize(float[] vector) {
        double magnitude = 0;
        for (float value : vector) {
            magnitude += value * value;
        }
        if (magnitude == 0) {
            return;
        }
        float divisor = (float) Math.sqrt(magnitude);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= divisor;
        }
    }
}
