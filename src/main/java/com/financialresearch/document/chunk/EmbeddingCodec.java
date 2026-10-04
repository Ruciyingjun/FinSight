/**
 * 向量编码器：以 Base64 浮点字节序列在关系数据库 TEXT 字段中无损保存向量。
 */
package com.financialresearch.document.chunk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

final class EmbeddingCodec {

    private EmbeddingCodec() {
    }

    static String encode(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.BIG_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    static float[] decode(String encoded, int dimensions) {
        if (encoded == null || dimensions <= 0) {
            return new float[0];
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length != dimensions * Float.BYTES) {
            throw new IllegalStateException("持久化向量的长度与维度不一致");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        float[] values = new float[dimensions];
        for (int index = 0; index < dimensions; index++) {
            values[index] = buffer.getFloat();
        }
        return values;
    }
}
