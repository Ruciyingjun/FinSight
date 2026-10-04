/**
 * 系统状态响应模型：描述服务状态及生成响应的时间。
 */
package com.financialresearch.system;

import java.time.Instant;

public record SystemStatusResponse(String status, Instant timestamp) {
}
