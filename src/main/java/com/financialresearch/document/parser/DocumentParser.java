/**
 * 文档解析器接口：为 PDF、Office 和 OCR 等不同解析实现提供统一协议。
 */
package com.financialresearch.document.parser;

import java.io.InputStream;
import java.util.List;

public interface DocumentParser {

    boolean supports(String filename);

    List<ParsedPage> parse(InputStream input);
}
