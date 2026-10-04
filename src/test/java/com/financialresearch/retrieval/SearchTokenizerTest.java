/**
 * 检索分词器单元测试：验证英文归一化以及中文单字、双字词元生成规则。
 */
package com.financialresearch.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTokenizerTest {

    private final SearchTokenizer tokenizer = new SearchTokenizer();

    @Test
    void tokenizesMixedChineseAndEnglishText() {
        assertThat(tokenizer.tokenize("Insurance 保险条款2026"))
                .containsExactly("insurance", "保", "险", "条", "款", "保险", "险条", "条款", "2026");
    }
}
