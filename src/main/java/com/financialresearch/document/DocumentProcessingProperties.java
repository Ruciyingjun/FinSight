/**
 * 异步文档处理配置：定义后台工作线程数量和等待队列容量。
 */
package com.financialresearch.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.document-processing")
public record DocumentProcessingProperties(int workers, int queueCapacity) {

    public DocumentProcessingProperties {
        if (workers < 1) {
            workers = 2;
        }
        if (queueCapacity < 1) {
            queueCapacity = 20;
        }
    }
}
