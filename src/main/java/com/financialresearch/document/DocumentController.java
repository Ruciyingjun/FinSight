/**
 * 文档 HTTP 接口：提供文档上传、列表、详情和删除操作。
 */
package com.financialresearch.document;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.financialresearch.document.chunk.DocumentChunkResponse;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file) {
        DocumentResponse uploaded = service.upload(file);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + uploaded.id())).body(uploaded);
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<DocumentResponse> process(@PathVariable String id) {
        return ResponseEntity.accepted().body(service.process(id));
    }

    @GetMapping("/{id}/pages")
    public List<DocumentPageResponse> pages(@PathVariable String id) {
        return service.pages(id);
    }

    @GetMapping("/{id}/pages/{pageNumber}")
    public DocumentPageResponse page(@PathVariable String id, @PathVariable int pageNumber) {
        return service.page(id, pageNumber);
    }

    @GetMapping("/{id}/chunks")
    public List<DocumentChunkResponse> chunks(@PathVariable String id) {
        return service.chunks(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> originalFile(@PathVariable String id) {
        DocumentFileContent file = service.openOriginalFile(id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.inputStream()));
    }

    @GetMapping("/{id}/file/highlight")
    public ResponseEntity<InputStreamResource> highlightedPdf(
            @PathVariable String id,
            @RequestParam int page,
            @RequestParam int start,
            @RequestParam int end) {
        DocumentFileContent file = service.openHighlightedPdf(id, page, start, end);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(file.inputStream()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private MediaType mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
