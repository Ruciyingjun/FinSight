/**
 * Agent 查询计划：保存模型选择的动作、检索策略、可选的检索改写词和直接响应内容。
 */
package com.financialresearch.question;

public record QueryPlan(QueryAction action, RetrievalStrategy retrievalStrategy,
        String retrievalQuery, String response) {

    public QueryPlan(QueryAction action, String retrievalQuery, String response) {
        this(action, RetrievalStrategy.FACT_SEARCH, retrievalQuery, response);
    }

    public static QueryPlan retrieve(String question) {
        return new QueryPlan(QueryAction.RETRIEVE, RetrievalStrategy.FACT_SEARCH, question, null);
    }
}
