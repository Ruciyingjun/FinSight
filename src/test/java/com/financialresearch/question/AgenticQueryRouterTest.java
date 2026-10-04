/**
 * Agentic 查询路由测试：验证模型动作被采用、检索词被规范化及调用失败时安全降级。
 */
package com.financialresearch.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class AgenticQueryRouterTest {

    private final LlmProperties enabled = new LlmProperties(
            true, "http://localhost/unused", "key", "model", 5, 200, 0.1);

    @Test
    void usesModelDecisionInsteadOfFixedGreetingRules() {
        AgenticQueryRouter router = new AgenticQueryRouter(enabled, client(
                new QueryPlan(QueryAction.DIRECT_ANSWER, null, "你好，我可以帮你分析资料。")));

        QueryPlan plan = router.plan("用一种以前没有配置过的方式向我打招呼", null);

        assertThat(plan.action()).isEqualTo(QueryAction.DIRECT_ANSWER);
        assertThat(plan.response()).contains("你好");
    }

    @Test
    void acceptsModelRewrittenRetrievalQuery() {
        AgenticQueryRouter router = new AgenticQueryRouter(enabled, client(
                new QueryPlan(QueryAction.RETRIEVE, "美的集团 盈利能力 三年趋势", null)));

        QueryPlan plan = router.plan("它近几年赚得怎么样？", "美的集团半年报.pdf");

        assertThat(plan.action()).isEqualTo(QueryAction.RETRIEVE);
        assertThat(plan.retrievalQuery()).isEqualTo("美的集团 盈利能力 三年趋势");
    }

    @Test
    void tellsPlannerWhenOneDocumentIsSelected() {
        AtomicReference<String> receivedDocumentName = new AtomicReference<>();
        LlmClient contextAware = new LlmClient() {
            @Override
            public String generate(AnswerContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public QueryPlan plan(String question, String selectedDocumentName) {
                receivedDocumentName.set(selectedDocumentName);
                return new QueryPlan(QueryAction.RETRIEVE, RetrievalStrategy.DOCUMENT_SUMMARY,
                        "项目经历 技术技能 教育背景", null);
            }
        };
        AgenticQueryRouter router = new AgenticQueryRouter(enabled, contextAware);

        QueryPlan plan = router.plan("这份文档的核心内容是什么？", "简历.pdf");

        assertThat(receivedDocumentName).hasValue("简历.pdf");
        assertThat(plan.action()).isEqualTo(QueryAction.RETRIEVE);
        assertThat(plan.retrievalStrategy()).isEqualTo(RetrievalStrategy.DOCUMENT_SUMMARY);
    }

    @Test
    void fallsBackToRetrievalWhenPlannerFails() {
        LlmClient failing = new LlmClient() {
            @Override
            public String generate(AnswerContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public QueryPlan plan(String question, String selectedDocumentName) {
                throw new LlmGenerationException("连接失败");
            }
        };
        AgenticQueryRouter router = new AgenticQueryRouter(enabled, failing);

        QueryPlan plan = router.plan("报告风险是什么？", null);

        assertThat(plan).isEqualTo(QueryPlan.retrieve("报告风险是什么？"));
    }

    private LlmClient client(QueryPlan plan) {
        return new LlmClient() {
            @Override
            public String generate(AnswerContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public QueryPlan plan(String question, String selectedDocumentName) {
                return plan;
            }
        };
    }
}
