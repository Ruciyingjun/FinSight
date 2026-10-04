/**
 * PDF 文档解析器：使用 Apache PDFBox 按页提取可搜索 PDF 中的文本。
 */
package com.financialresearch.document.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class PdfDocumentParser implements DocumentParser {

    private final OcrEngine ocrEngine;
    private final OcrProperties ocrProperties;

    PdfDocumentParser(OcrEngine ocrEngine, OcrProperties ocrProperties) {
        this.ocrEngine = ocrEngine;
        this.ocrProperties = ocrProperties;
    }

    @Override
    public boolean supports(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    @Override
    public List<ParsedPage> parse(InputStream input) {
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            if (document.isEncrypted()) {
                throw new DocumentParsingException("暂不支持加密 PDF", null);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            List<ParsedPage> pages = new ArrayList<>(document.getNumberOfPages());
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String content = normalize(stripper.getText(document));
                if (content.length() < ocrProperties.minimumTextCharacters() && ocrProperties.enabled()) {
                    content = normalize(ocrEngine.recognize(
                            renderer.renderImageWithDPI(pageNumber - 1, ocrProperties.dpi()), pageNumber));
                }
                pages.add(new ParsedPage(pageNumber, content));
            }
            if (pages.stream().allMatch(page -> page.content().isBlank())) {
                String message = ocrProperties.enabled()
                        ? "PDF 中没有识别到可检索文字"
                        : "PDF 中没有可提取文字；如为扫描件，请安装 Tesseract 并启用 OCR";
                throw new DocumentParsingException(message, null);
            }
            return pages;
        } catch (DocumentParsingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DocumentParsingException("PDF 解析失败", exception);
        }
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::stripTrailing)
                .collect(java.util.stream.Collectors.joining("\n"))
                .strip();
    }
}
