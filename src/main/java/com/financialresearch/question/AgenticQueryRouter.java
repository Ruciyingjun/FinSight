/**
 * Agentic 查询路由器：让大模型选择直接回答、文档检索或澄清，而不维护固定话术集合。
 */
package com.financialresearch.question;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class AgenticQueryRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticQueryRouter.class);

    private final LlmProperties properties;
    private final LlmClient llmClient;

    AgenticQueryRouter(LlmProperties properties, LlmClient llmClient) {
        this.properties = properties;
        this.llmClient = llmClient;
    }

    QueryPlan plan(String question, String selectedDocumentName) {
        if (!properties.enabled()) {
            return QueryPlan.retrieve(question);
        }
        try {
            return normalize(llmClient.plan(question, selectedDocumentName), question);
        } catch (RuntimeException exception) {
            LOGGER.warn("Agentic 查询路由失败，已安全降级为文档检索: {}", exception.getMessage());
            return QueryPlan.retrieve(question);
        }
    }

    private QueryPlan normalize(QueryPlan plan, String originalQuestion) {
        if (plan == null || plan.action() == null) {
            throw new LlmGenerationException("查询路由没有返回有效动作");
        }
        if (plan.action() == QueryAction.RETRIEVE) {
            String query = plan.retrievalQuery();
            RetrievalStrategy strategy = plan.retrievalStrategy() == null
                    ? RetrievalStrategy.FACT_SEARCH : plan.retrievalStrategy();
            return new QueryPlan(QueryAction.RETRIEVE, strategy,
                    query == null || query.isBlank() ? originalQuestion : query.strip(), null);
        }
        String response = plan.response();
        if (response == null || response.isBlank()) {
            throw new LlmGenerationException("查询路由没有返回直接响应内容");
        }
        return new QueryPlan(plan.action(), null, null, response.strip());
    }
}
