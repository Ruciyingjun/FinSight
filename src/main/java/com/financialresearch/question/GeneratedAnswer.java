/**
 * 答案生成结果：同时携带答案正文和实际采用的生成来源，供接口与前端明确展示。
 */
package com.financialresearch.question;

public record GeneratedAnswer(String text, AnswerSource source) {
}
