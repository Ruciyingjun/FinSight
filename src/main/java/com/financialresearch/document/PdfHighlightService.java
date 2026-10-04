/**
 * PDF 引用高亮服务：在原始 PDF 的目标页匹配引用文本坐标，并写入标准黄色 Highlight 批注。
 */
package com.financialresearch.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

@Service
class PdfHighlightService {

    byte[] highlight(InputStream input, int pageNumber, String quote) {
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
                throw new InvalidDocumentException("PDF 第 " + pageNumber + " 页不存在");
            }
            PositionedText text = extract(document, pageNumber);
            String target = compact(quote);
            int matchStart = text.compactText().indexOf(target);
            if (target.isBlank() || matchStart < 0) {
                throw new InvalidDocumentException("无法在原 PDF 页面中定位引用文字");
            }
            List<TextPosition> matched = text.positions().subList(matchStart, matchStart + target.length());
            addHighlights(document.getPage(pageNumber - 1), matched);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (InvalidDocumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DocumentStorageException("生成 PDF 引用高亮失败", exception);
        }
    }

    private PositionedText extract(PDDocument document, int pageNumber) throws IOException {
        PositionCollectingStripper stripper = new PositionCollectingStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        stripper.getText(document);
        return new PositionedText(stripper.text(), List.copyOf(stripper.positions()));
    }

    private void addHighlights(PDPage page, List<TextPosition> positions) throws IOException {
        List<LineBounds> lines = lineBounds(positions);
        for (LineBounds line : lines) {
            PDAnnotationHighlight annotation = new PDAnnotationHighlight();
            annotation.setColor(new PDColor(new float[]{1f, 0.82f, 0.12f}, PDDeviceRGB.INSTANCE));
            annotation.setConstantOpacity(0.38f);
            annotation.setQuadPoints(new float[]{
                    line.left(), line.top(), line.right(), line.top(),
                    line.left(), line.bottom(), line.right(), line.bottom()
            });
            annotation.setRectangle(new PDRectangle(
                    line.left(), line.bottom(), line.right() - line.left(), line.top() - line.bottom()));
            page.getAnnotations().add(annotation);
        }
    }

    private List<LineBounds> lineBounds(List<TextPosition> positions) {
        List<LineBounds> lines = new ArrayList<>();
        LineBounds current = null;
        for (TextPosition position : positions) {
            float left = position.getXDirAdj();
            float right = left + position.getWidthDirAdj();
            float baseline = position.getPageHeight() - position.getYDirAdj();
            float top = baseline + Math.max(position.getHeightDir(), position.getFontSizeInPt());
            float bottom = baseline;
            if (current == null || Math.abs(current.baseline() - baseline) > 3f || left + 4f < current.left()) {
                current = new LineBounds(left, right, top, bottom, baseline);
                lines.add(current);
            } else {
                current.expand(left, right, top, bottom);
            }
        }
        return lines;
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private record PositionedText(String compactText, List<TextPosition> positions) {
    }

    private static final class PositionCollectingStripper extends PDFTextStripper {

        private final StringBuilder text = new StringBuilder();
        private final List<TextPosition> positions = new ArrayList<>();

        private PositionCollectingStripper() throws IOException {
        }

        @Override
        protected void writeString(String value, List<TextPosition> textPositions) {
            for (TextPosition position : textPositions) {
                String unicode = position.getUnicode();
                if (unicode == null) continue;
                for (int index = 0; index < unicode.length(); index++) {
                    char character = unicode.charAt(index);
                    if (!Character.isWhitespace(character)) {
                        text.append(character);
                        positions.add(position);
                    }
                }
            }
        }

        private String text() {
            return text.toString();
        }

        private List<TextPosition> positions() {
            return positions;
        }
    }

    private static final class LineBounds {
        private float left;
        private float right;
        private float top;
        private float bottom;
        private final float baseline;

        private LineBounds(float left, float right, float top, float bottom, float baseline) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.baseline = baseline;
        }

        private void expand(float newLeft, float newRight, float newTop, float newBottom) {
            left = Math.min(left, newLeft);
            right = Math.max(right, newRight);
            top = Math.max(top, newTop);
            bottom = Math.min(bottom, newBottom);
        }

        private float left() { return left; }
        private float right() { return right; }
        private float top() { return top; }
        private float bottom() { return bottom; }
        private float baseline() { return baseline; }
    }
}
