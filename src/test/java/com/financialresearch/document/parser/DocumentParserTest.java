/**
 * 文档解析器测试：使用真实 DOCX 内容及带 BOM 的 UTF-8 文本验证新增解析能力。
 */
package com.financialresearch.document.parser;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class DocumentParserTest {

    @Test
    void extractsTextFromDocx() throws Exception {
        byte[] content;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("保险责任说明");
            document.createParagraph().createRun().setText("包含医疗保障");
            document.write(output);
            content = output.toByteArray();
        }

        OfficeDocumentParser parser = new OfficeDocumentParser();
        var pages = parser.parse(new ByteArrayInputStream(content));

        assertThat(pages).hasSize(1);
        assertThat(pages.getFirst().content()).contains("保险责任说明", "包含医疗保障");
    }

    @Test
    void decodesUtf8TextAndRemovesBom() {
        byte[] text = "\uFEFF标题\r\n正文  \r\n".getBytes(UTF_8);

        PlainTextDocumentParser parser = new PlainTextDocumentParser();
        var pages = parser.parse(new ByteArrayInputStream(text));

        assertThat(pages).containsExactly(new ParsedPage(1, "标题\n正文"));
    }

    @Test
    void usesOcrForPdfPageWithoutEmbeddedText() throws Exception {
        byte[] pdf = blankPdf();
        OcrProperties properties = new OcrProperties(true, "tesseract", "chi_sim+eng", 150, 10, 5);
        OcrEngine engine = (image, pageNumber) -> "扫描页保险责任";

        PdfDocumentParser parser = new PdfDocumentParser(engine, properties);
        var pages = parser.parse(new ByteArrayInputStream(pdf));

        assertThat(pages).containsExactly(new ParsedPage(1, "扫描页保险责任"));
    }

    @Test
    void explainsHowToEnableOcrForScannedPdf() throws Exception {
        byte[] pdf = blankPdf();
        OcrProperties properties = new OcrProperties(false, "tesseract", "chi_sim+eng", 150, 10, 5);
        PdfDocumentParser parser = new PdfDocumentParser((image, pageNumber) -> "不应调用", properties);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> parser.parse(new ByteArrayInputStream(pdf)))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessageContaining("启用 OCR");
    }

    private byte[] blankPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
