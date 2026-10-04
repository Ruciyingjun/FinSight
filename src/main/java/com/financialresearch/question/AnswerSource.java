/**
 * 答案来源枚举：区分 Agent 路由响应、大模型证据回答与本地抽取式回退。
 */
package com.financialresearch.question;

public enum AnswerSource {
    AGENT_RESPONSE,
    LARGE_LANGUAGE_MODEL,
    LOCAL_FALLBACK
}
