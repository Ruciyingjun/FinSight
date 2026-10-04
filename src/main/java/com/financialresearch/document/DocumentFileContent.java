/**
 * 原始文档内容模型：向 HTTP 层传递安全文件名、媒体类型、长度和存储输入流。
 */
package com.financialresearch.document;

import java.io.InputStream;

record DocumentFileContent(
        String filename,
        String contentType,
        long sizeBytes,
        InputStream inputStream) {
}
