/**
 * 问答请求模型：接收用户问题、可选文档范围和需要引用的检索结果数量。
 */
package com.financialresearch.question;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题不能超过 500 个字符")
        String question,

        @Size(max = 36, message = "文档 ID 长度不能超过 36")
        String documentId,

        @Min(value = 1, message = "limit 不能小于 1")
        @Max(value = 10, message = "limit 不能大于 10")
        Integer limit) {

    int effectiveLimit() {
        return limit == null ? 5 : limit;
    }
}
