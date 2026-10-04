/**
 * 文档存储抽象：隔离业务层与本地磁盘、对象存储等具体存储实现。
 */
package com.financialresearch.document.storage;

import java.io.InputStream;

public interface DocumentStorage {

    void store(String storedFilename, InputStream content);

    InputStream open(String storedFilename);

    void delete(String storedFilename);
}
