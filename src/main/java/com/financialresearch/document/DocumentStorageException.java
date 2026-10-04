/**
 * 文档存储异常：封装文件读取、保存、删除或摘要计算过程中的底层错误。
 */
package com.financialresearch.document;

public class DocumentStorageException extends RuntimeException {

    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
