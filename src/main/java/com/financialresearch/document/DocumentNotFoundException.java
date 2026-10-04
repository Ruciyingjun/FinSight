/**
 * 文档不存在异常：在请求的文档标识无法匹配持久化记录时抛出。
 */
package com.financialresearch.document;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String id) {
        super("未找到文档: " + id);
    }
}
