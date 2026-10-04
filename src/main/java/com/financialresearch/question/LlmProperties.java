/**
 * 大模型配置：定义 OpenAI 兼容接口的开关、地址、密钥、模型和生成参数。
 */
package com.financialresearch.question;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.llm")
public record LlmProperties(
        boolean enabled,
        String endpoint,
        String apiKey,
        String model,
        int timeoutSeconds,
        int maxTokens,
        double temperature) {

    public LlmProperties {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("LLM endpoint 不能为空");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("LLM model 不能为空");
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 300) {
            throw new IllegalArgumentException("LLM timeoutSeconds 必须在 1 到 300 之间");
        }
        if (maxTokens < 64 || maxTokens > 32768) {
            throw new IllegalArgumentException("LLM maxTokens 必须在 64 到 32768 之间");
        }
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("LLM temperature 必须在 0 到 2 之间");
        }
        apiKey = apiKey == null ? "" : apiKey;
    }
}
