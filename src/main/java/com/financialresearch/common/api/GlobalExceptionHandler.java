/**
 * 全局异常处理器：将文档、存储和参数校验异常转换为统一 HTTP 错误响应。
 */
package com.financialresearch.common.api;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.financialresearch.document.DocumentNotFoundException;
import com.financialresearch.document.DocumentProcessingQueueFullException;
import com.financialresearch.document.DocumentStorageException;
import com.financialresearch.document.InvalidDocumentException;
import com.financialresearch.document.InvalidDocumentStateException;
import com.financialresearch.retrieval.InvalidSearchQueryException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(DocumentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("DOCUMENT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ResponseEntity<ApiError> handleInvalidDocument(InvalidDocumentException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_DOCUMENT", exception.getMessage()));
    }

    @ExceptionHandler(InvalidDocumentStateException.class)
    ResponseEntity<ApiError> handleInvalidState(InvalidDocumentStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("INVALID_DOCUMENT_STATE", exception.getMessage()));
    }

    @ExceptionHandler(InvalidSearchQueryException.class)
    ResponseEntity<ApiError> handleInvalidSearch(InvalidSearchQueryException exception) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_SEARCH_QUERY", exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleFileTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("FILE_TOO_LARGE", "上传文件超过允许的大小"));
    }

    @ExceptionHandler(DocumentStorageException.class)
    ResponseEntity<ApiError> handleStorageFailure(DocumentStorageException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("STORAGE_FAILURE", exception.getMessage()));
    }

    @ExceptionHandler(DocumentProcessingQueueFullException.class)
    ResponseEntity<ApiError> handleProcessingQueueFull(DocumentProcessingQueueFullException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("DOCUMENT_PROCESSING_QUEUE_FULL", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> details = exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "参数无效" : error.getDefaultMessage(),
                        (first, ignored) -> first));
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "请求参数校验失败", Instant.now(), details));
    }
}
