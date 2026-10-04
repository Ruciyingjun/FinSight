/**
 * 上下文段落模型：保存去重后的证据文本及其对应的一个或多个引用编号。
 */
package com.financialresearch.question;

import java.util.List;

public record ContextPassage(List<Integer> citationNumbers, String content) {
}
