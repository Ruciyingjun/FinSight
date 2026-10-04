/**
 * 文本切块器接口：将解析后的页面转换为带原文位置的有序文本块。
 */
package com.financialresearch.document.chunk;

import java.util.List;

import com.financialresearch.document.parser.ParsedPage;

public interface TextChunker {

    List<TextChunk> chunk(List<ParsedPage> pages);
}
