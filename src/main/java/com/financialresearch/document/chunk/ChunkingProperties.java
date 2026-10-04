/**
 * 文本切块配置：控制每个块的最大字符数及相邻块之间的重叠字符数。
 */
package com.financialresearch.document.chunk;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.chunking")
public record ChunkingProperties(int maxCharacters, int overlapCharacters) {

    public ChunkingProperties {
        if (maxCharacters < 100) {
            throw new IllegalArgumentException("maxCharacters 不能小于 100");
        }
        if (overlapCharacters < 0 || overlapCharacters >= maxCharacters) {
            throw new IllegalArgumentException("overlapCharacters 必须大于等于 0 且小于 maxCharacters");
        }
    }
}
