/**
 * 文档应用服务：负责上传校验、文件存储、摘要计算、处理调度、元数据查询和删除。
 */
package com.financialresearch.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.financialresearch.document.storage.DocumentStorage;
import com.financialresearch.document.chunk.DocumentChunkResponse;

@Service
public class DocumentService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv");

    private final DocumentRepository repository;
    private final DocumentStorage storage;
    private final DocumentProcessingService processingService;
    private final PdfHighlightService pdfHighlightService;

    public DocumentService(DocumentRepository repository, DocumentStorage storage,
            DocumentProcessingService processingService, PdfHighlightService pdfHighlightService) {
        this.repository = repository;
        this.storage = storage;
        this.processingService = processingService;
        this.pdfHighlightService = pdfHighlightService;
    }

    @Transactional
    public DocumentResponse upload(MultipartFile file) {
        String originalFilename = validateAndNormalizeFilename(file);
        String id = UUID.randomUUID().toString();
        String storedFilename = id + extensionOf(originalFilename);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new DocumentStorageException("读取上传文档失败", exception);
        }

        storage.store(storedFilename, new java.io.ByteArrayInputStream(content));
        try {
            Instant now = Instant.now();
            DocumentEntity saved = repository.saveAndFlush(new DocumentEntity(
                    id,
                    originalFilename,
                    storedFilename,
                    file.getContentType(),
                    content.length,
                    sha256(new java.io.ByteArrayInputStream(content)),
                    now));
            return DocumentResponse.from(saved);
        } catch (RuntimeException exception) {
            storage.delete(storedFilename);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(String id) {
        return DocumentResponse.from(find(id));
    }

    public DocumentResponse process(String id) {
        return processingService.enqueue(id);
    }

    @Transactional(readOnly = true)
    public List<DocumentPageResponse> pages(String id) {
        find(id);
        return processingService.pages(id);
    }

    @Transactional(readOnly = true)
    public DocumentPageResponse page(String id, int pageNumber) {
        find(id);
        if (pageNumber < 1) {
            throw new InvalidDocumentException("页码必须大于 0");
        }
        return processingService.page(id, pageNumber);
    }

    @Transactional(readOnly = true)
    public List<DocumentChunkResponse> chunks(String id) {
        find(id);
        return processingService.chunks(id);
    }

    @Transactional(readOnly = true)
    public DocumentFileContent openOriginalFile(String id) {
        DocumentEntity document = find(id);
        return new DocumentFileContent(
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                storage.open(document.getStoredFilename()));
    }

    @Transactional(readOnly = true)
    public DocumentFileContent openHighlightedPdf(String id, int pageNumber, int startOffset, int endOffset) {
        DocumentEntity document = find(id);
        if (!document.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new InvalidDocumentException("只有 PDF 文档支持在原文件中直接高亮");
        }
        DocumentPageResponse page = processingService.page(id, pageNumber);
        if (startOffset < 0 || endOffset <= startOffset || endOffset > page.content().length()) {
            throw new InvalidDocumentException("引用字符范围无效");
        }
        String quote = page.content().substring(startOffset, endOffset);
        byte[] highlighted;
        try (InputStream input = storage.open(document.getStoredFilename())) {
            highlighted = pdfHighlightService.highlight(input, pageNumber, quote);
        } catch (IOException exception) {
            throw new DocumentStorageException("读取原始 PDF 失败", exception);
        }
        String filename = document.getOriginalFilename().replaceFirst("(?i)\\.pdf$", "-引用高亮.pdf");
        return new DocumentFileContent(filename, MediaType.APPLICATION_PDF_VALUE,
                highlighted.length, new ByteArrayInputStream(highlighted));
    }

    @Transactional
    public void delete(String id) {
        DocumentEntity document = find(id);
        if (document.getStatus() == DocumentStatus.QUEUED
                || document.getStatus() == DocumentStatus.PARSING
                || document.getStatus() == DocumentStatus.CHUNKING
                || document.getStatus() == DocumentStatus.INDEXING) {
            throw new InvalidDocumentStateException(id, document.getStatus(), DocumentStatus.FAILED);
        }
        storage.delete(document.getStoredFilename());
        repository.delete(document);
    }

    private DocumentEntity find(String id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private String validateAndNormalizeFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new InvalidDocumentException("文件名不能为空");
        }
        String normalized = filename.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isBlank() || normalized.length() > 512) {
            throw new InvalidDocumentException("文件名无效或过长");
        }
        String extension = extensionOf(normalized).replace(".", "").toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new InvalidDocumentException("暂不支持该文件类型: " + extension);
        }
        return normalized;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String sha256(InputStream input) {
        try (DigestInputStream digestInput = new DigestInputStream(input, MessageDigest.getInstance("SHA-256"))) {
            digestInput.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(digestInput.getMessageDigest().digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new DocumentStorageException("计算文档摘要失败", exception);
        }
    }
}
