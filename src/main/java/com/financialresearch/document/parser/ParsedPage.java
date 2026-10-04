/**
 * 解析页面模型：在解析器和持久化层之间传递页码及提取后的文本。
 */
package com.financialresearch.document.parser;

public record ParsedPage(int pageNumber, String content) {
}
