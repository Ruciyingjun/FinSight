/**
 * 无效文档异常：表示上传文件为空、文件名非法或文件类型不受支持。
 */
package com.financialresearch.document;

public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
