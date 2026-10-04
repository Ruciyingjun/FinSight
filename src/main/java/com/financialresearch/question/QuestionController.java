/**
 * 问答 HTTP 接口：校验用户问题并返回带原文引用的答案响应。
 */
package com.financialresearch.question;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService service;
    private final LlmProperties llmProperties;
    private final ThreadPoolTaskExecutor streamingExecutor;

    public QuestionController(QuestionService service, LlmProperties llmProperties,
            @Qualifier("questionStreamingExecutor") ThreadPoolTaskExecutor streamingExecutor) {
        this.service = service;
        this.llmProperties = llmProperties;
        this.streamingExecutor = streamingExecutor;
    }

    @PostMapping
    public QuestionResponse ask(@Valid @RequestBody QuestionRequest request) {
        return service.ask(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody QuestionRequest request) {
        SseEmitter emitter = new SseEmitter((llmProperties.timeoutSeconds() + 30L) * 1000L);
        AtomicReference<Future<?>> task = new AtomicReference<>();
        emitter.onTimeout(() -> {
            Future<?> running = task.get();
            if (running != null) {
                running.cancel(true);
            }
            emitter.complete();
        });
        try {
            task.set(streamingExecutor.submit(() -> streamAnswer(request, emitter)));
        } catch (RejectedExecutionException exception) {
            trySend(emitter, "error", Map.of("message", "当前问答请求过多，请稍后重试"));
            emitter.complete();
        }
        return emitter;
    }

    private void streamAnswer(QuestionRequest request, SseEmitter emitter) {
        try {
            PreparedQuestion prepared = service.prepare(request);
            emitter.send(SseEmitter.event().name("context").data(Map.of(
                    "question", prepared.question(),
                    "evidenceFound", !prepared.citations().isEmpty(),
                    "citations", prepared.citations())));
            QuestionResponse response = service.generateStreaming(prepared,
                    token -> sendToken(emitter, token));
            emitter.send(SseEmitter.event().name("complete").data(response));
            emitter.complete();
        } catch (ClientDisconnectedException exception) {
            emitter.complete();
        } catch (Exception exception) {
            trySend(emitter, "error", Map.of("message", rootMessage(exception)));
            emitter.complete();
        }
    }

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(Map.of("text", token)));
        } catch (IOException | IllegalStateException exception) {
            throw new ClientDisconnectedException(exception);
        }
    }

    private void trySend(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException ignored) {
            // 客户端已经离开时不再尝试写入连接。
        }
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "流式问答失败" : current.getMessage();
    }

    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
