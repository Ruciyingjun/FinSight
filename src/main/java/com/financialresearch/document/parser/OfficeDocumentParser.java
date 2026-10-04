/**
 * Office 文档解析器：使用 Apache POI 从 Word、Excel 和 PowerPoint 新旧格式中提取可检索文本。
 */
package com.financialresearch.document.parser;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.springframework.stereotype.Component;

@Component
class OfficeDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of(
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx");

    @Override
    public boolean supports(String filename) {
        String normalized = filename.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(normalized::endsWith);
    }

    @Override
    public List<ParsedPage> parse(InputStream input) {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(input)) {
            String content = normalize(extractor.getText());
            if (content.isBlank()) {
                throw new DocumentParsingException("Office 文档中没有可提取的文本", null);
            }
            return List.of(new ParsedPage(1, content));
        } catch (DocumentParsingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentParsingException("Office 文档解析失败", exception);
        }
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::stripTrailing)
                .filter(line -> !line.isBlank())
                .collect(java.util.stream.Collectors.joining("\n"))
                .strip();
    }
}
