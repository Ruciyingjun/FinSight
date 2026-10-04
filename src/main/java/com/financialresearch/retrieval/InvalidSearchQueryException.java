/**
 * 无效检索异常：表示查询为空、过长或返回数量超出允许范围。
 */
package com.financialresearch.retrieval;

public class InvalidSearchQueryException extends RuntimeException {

    public InvalidSearchQueryException(String message) {
        super(message);
    }
}
