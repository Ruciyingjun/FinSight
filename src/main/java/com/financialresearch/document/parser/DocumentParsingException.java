/**
 * 文档解析异常：统一封装损坏文件、加密文件或解析库产生的错误。
 */
package com.financialresearch.document.parser;

public class DocumentParsingException extends RuntimeException {

    public DocumentParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
