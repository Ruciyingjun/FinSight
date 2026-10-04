/**
 * OCR 引擎接口：隔离扫描图像文字识别实现，便于替换为云服务或其他本地引擎。
 */
package com.financialresearch.document.parser;

import java.awt.image.BufferedImage;

public interface OcrEngine {

    String recognize(BufferedImage image, int pageNumber);
}
