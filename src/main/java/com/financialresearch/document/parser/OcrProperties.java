/**
 * OCR 配置：控制 Tesseract 开关、命令路径、识别语言、渲染清晰度和超时时间。
 */
package com.financialresearch.document.parser;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ocr")
public record OcrProperties(
        boolean enabled,
        String executable,
        String languages,
        int dpi,
        int timeoutSeconds,
        int minimumTextCharacters) {

    public OcrProperties {
        if (executable == null || executable.isBlank()) {
            throw new IllegalArgumentException("OCR executable 不能为空");
        }
        if (languages == null || languages.isBlank()) {
            throw new IllegalArgumentException("OCR languages 不能为空");
        }
        if (dpi < 100 || dpi > 600) {
            throw new IllegalArgumentException("OCR dpi 必须在 100 到 600 之间");
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 300) {
            throw new IllegalArgumentException("OCR timeoutSeconds 必须在 1 到 300 之间");
        }
        if (minimumTextCharacters < 0 || minimumTextCharacters > 1000) {
            throw new IllegalArgumentException("OCR minimumTextCharacters 必须在 0 到 1000 之间");
        }
    }
}
