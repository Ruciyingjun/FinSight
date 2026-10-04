/**
 * 大模型生成异常：统一表示网络失败、非成功状态码或无效模型响应。
 */
package com.financialresearch.question;

class LlmGenerationException extends RuntimeException {

    LlmGenerationException(String message) {
        super(message);
    }

    LlmGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
