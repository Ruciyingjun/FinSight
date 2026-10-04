/**
 * 检索策略：区分局部事实查询与需要跨文档范围取证的全文总结任务。
 */
package com.financialresearch.question;

public enum RetrievalStrategy {
    FACT_SEARCH,
    DOCUMENT_SUMMARY
}
