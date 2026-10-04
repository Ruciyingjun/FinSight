/**
 * Tesseract OCR 引擎：将页面图片写入临时目录并通过受控进程调用本地 Tesseract。
 */
package com.financialresearch.document.parser;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

@Component
class TesseractOcrEngine implements OcrEngine {

    private final OcrProperties properties;

    TesseractOcrEngine(OcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String recognize(BufferedImage image, int pageNumber) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("financial-research-ocr-");
            Path imagePath = directory.resolve("page.png");
            Path outputBase = directory.resolve("result");
            Path errorPath = directory.resolve("tesseract-error.log");
            if (!ImageIO.write(image, "png", imagePath.toFile())) {
                throw new DocumentParsingException("无法生成 OCR 页面图片", null);
            }

            List<String> command = List.of(
                    properties.executable(), imagePath.toString(), outputBase.toString(),
                    "-l", properties.languages(), "--dpi", Integer.toString(properties.dpi()));
            Process process = new ProcessBuilder(command)
                    .redirectError(errorPath.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DocumentParsingException("OCR 第 " + pageNumber + " 页处理超时", null);
            }
            String error = Files.exists(errorPath)
                    ? Files.readString(errorPath, StandardCharsets.UTF_8).strip() : "";
            if (process.exitValue() != 0) {
                throw new DocumentParsingException(
                        "OCR 第 " + pageNumber + " 页失败" + (error.isBlank() ? "" : ": " + error), null);
            }
            Path resultPath = directory.resolve("result.txt");
            return Files.exists(resultPath)
                    ? Files.readString(resultPath, StandardCharsets.UTF_8).strip() : "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DocumentParsingException("OCR 处理被中断", exception);
        } catch (IOException exception) {
            throw new DocumentParsingException(
                    "无法启动 OCR，请检查 Tesseract 是否已安装以及 OCR_EXECUTABLE 配置", exception);
        } finally {
            deleteTemporaryDirectory(directory);
        }
    }

    private void deleteTemporaryDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时文件清理失败不覆盖主要 OCR 结果或异常。
                }
            });
        } catch (IOException ignored) {
            // 临时目录清理失败不影响文档处理结果。
        }
    }
}
