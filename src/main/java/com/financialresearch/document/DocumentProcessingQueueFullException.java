/**
 * 文档处理队列异常：在后台工作线程和等待队列均已占满时通知客户端稍后重试。
 */
package com.financialresearch.document;

public class DocumentProcessingQueueFullException extends RuntimeException {

    public DocumentProcessingQueueFullException() {
        super("后台文档处理队列已满，请稍后重试");
    }
}
