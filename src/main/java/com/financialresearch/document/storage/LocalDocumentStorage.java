/**
 * 本地文档存储实现：安全地在配置目录中保存和删除上传文件。
 */
package com.financialresearch.document.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Component;

import com.financialresearch.document.DocumentStorageException;

@Component
class LocalDocumentStorage implements DocumentStorage {

    private final Path root;

    LocalDocumentStorage(StorageProperties properties) {
        this.root = properties.root().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new DocumentStorageException("无法创建文档存储目录", exception);
        }
    }

    @Override
    public void store(String storedFilename, InputStream content) {
        Path destination = resolve(storedFilename);
        try (content) {
            Files.copy(content, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new DocumentStorageException("保存文档失败", exception);
        }
    }

    @Override
    public InputStream open(String storedFilename) {
        try {
            return Files.newInputStream(resolve(storedFilename));
        } catch (IOException exception) {
            throw new DocumentStorageException("读取文档文件失败", exception);
        }
    }

    @Override
    public void delete(String storedFilename) {
        try {
            Files.deleteIfExists(resolve(storedFilename));
        } catch (IOException exception) {
            throw new DocumentStorageException("删除文档文件失败", exception);
        }
    }

    private Path resolve(String storedFilename) {
        Path resolved = root.resolve(storedFilename).normalize();
        if (!resolved.startsWith(root)) {
            throw new DocumentStorageException("非法的文档存储路径", null);
        }
        return resolved;
    }
}
