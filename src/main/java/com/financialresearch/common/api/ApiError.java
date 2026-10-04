/**
 * 统一错误响应模型：向 API 调用方返回稳定的错误编码、信息和详情。
 */
package com.financialresearch.common.api;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        Instant timestamp,
        Map<String, String> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), Map.of());
    }
}
