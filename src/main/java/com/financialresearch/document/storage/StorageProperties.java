/**
 * 文档存储配置：将 app.storage 配置映射为类型安全的存储根目录参数。
 */
package com.financialresearch.document.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.storage")
public record StorageProperties(Path root) {
}
