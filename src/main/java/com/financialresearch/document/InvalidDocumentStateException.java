/**
 * 文档状态异常：阻止文档从当前状态进入不合法的下一处理状态。
 */
package com.financialresearch.document;

public class InvalidDocumentStateException extends RuntimeException {

    public InvalidDocumentStateException(String id, DocumentStatus current, DocumentStatus target) {
        super("文档 " + id + " 不能从 " + current + " 转换为 " + target);
    }
}
