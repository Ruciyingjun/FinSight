/**
 * 检索模式：允许调用方选择关键词、向量或基于排名融合的混合检索。
 */
package com.financialresearch.retrieval;

public enum RetrievalMode {
    KEYWORD,
    VECTOR,
    HYBRID
}
