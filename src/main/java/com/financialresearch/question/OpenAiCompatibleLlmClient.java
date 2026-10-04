/**
 * OpenAI 兼容大模型客户端：通过标准 chat/completions JSON 协议生成带证据引用的答案。
 */
package com.financialresearch.question;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
class OpenAiCompatibleLlmClient implements LlmClient {

    private static final String SYSTEM_PROMPT = """
            你是金融资料问答助手。只能使用用户提供的证据回答，不得补充证据之外的事实。
            每个事实陈述必须附带对应的引用编号，例如 [1]。如果证据不足，请明确说明无法回答。
            不要编造引用，不要输出证据列表中不存在的编号，使用简洁中文回答。
            """;
    private static final String ROUTER_PROMPT = """
            你是金融资料问答系统的查询规划 Agent。根据用户输入只选择一个下一步动作：
            1. DIRECT_ANSWER：问候、致谢、闲聊、系统能力询问等无需资料的问题；同时给出简短自然的 response。
            2. RETRIEVE：需要从用户上传的报告、合同、研报或其他资料寻找事实；给出适合检索的 retrievalQuery，不要回答问题。
            3. CLARIFY：问题缺少关键对象或含义不明确；在 response 中提出一个简短澄清问题。

            你还会收到“当前资料范围”。如果已选中一份具体文档，用户所说的“这份文档”“该报告”
            “这份资料”等指代应解析为该文档；凡是需要了解其内容的问题都选择 RETRIEVE，不要选择 CLARIFY。
            只有在未指定单份文档且确实无法确定检索对象时，才选择 CLARIFY。

            RETRIEVE 还必须选择 retrievalStrategy：
            - FACT_SEARCH：查询具体事实、条款、数字、人物或局部内容。
            - DOCUMENT_SUMMARY：概括整份文档、核心内容、主要结论或整体结构。
            DOCUMENT_SUMMARY 的 retrievalQuery 应结合文档名称推断文档类型，并扩展为能覆盖主要章节的主题词。
            例如财报应覆盖业务、经营、财务、战略和风险，简历应覆盖经历、项目、技能和教育；不要只复述“核心内容”。

            只输出一个 JSON 对象，不要输出 Markdown 或解释：
            {"action":"DIRECT_ANSWER|RETRIEVE|CLARIFY","retrievalStrategy":"FACT_SEARCH|DOCUMENT_SUMMARY","retrievalQuery":null,"response":null}
            RETRIEVE 必须填写 retrievalStrategy 和 retrievalQuery；其他动作必须填写 response。
            """;

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build());
    }

    OpenAiCompatibleLlmClient(LlmProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String generate(AnswerContext context) {
        try {
            HttpRequest.Builder request = request(context, false);
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmGenerationException("大模型接口返回状态码 " + response.statusCode());
            }
            JsonNode content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new LlmGenerationException("大模型返回内容为空或格式不正确");
            }
            return content.asText().strip();
        } catch (LlmGenerationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmGenerationException("大模型请求被中断", exception);
        } catch (Exception exception) {
            throw new LlmGenerationException("大模型请求失败", exception);
        }
    }

    @Override
    public QueryPlan plan(String question, String selectedDocumentName) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", properties.model());
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", ROUTER_PROMPT),
                    Map.of("role", "user", "content", planningPrompt(question, selectedDocumentName))));
            requestBody.put("temperature", 0);
            requestBody.put("max_tokens", Math.min(300, properties.maxTokens()));
            requestBody.put("stream", false);
            if (isDeepSeekEndpoint()) {
                requestBody.put("thinking", Map.of("type", "disabled"));
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.endpoint()))
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));
            if (!properties.apiKey().isBlank()) {
                request.header("Authorization", "Bearer " + properties.apiKey());
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmGenerationException("查询规划接口返回状态码 " + response.statusCode());
            }
            JsonNode content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new LlmGenerationException("查询规划返回内容为空");
            }
            JsonNode plan = objectMapper.readTree(jsonObject(content.asText()));
            QueryAction action = QueryAction.valueOf(plan.path("action").asText());
            RetrievalStrategy strategy = nullableText(plan.path("retrievalStrategy")) == null
                    ? RetrievalStrategy.FACT_SEARCH
                    : RetrievalStrategy.valueOf(plan.path("retrievalStrategy").asText());
            return new QueryPlan(
                    action,
                    strategy,
                    nullableText(plan.path("retrievalQuery")),
                    nullableText(plan.path("response")));
        } catch (LlmGenerationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmGenerationException("查询规划请求被中断", exception);
        } catch (Exception exception) {
            throw new LlmGenerationException("查询规划失败", exception);
        }
    }

    private String planningPrompt(String question, String selectedDocumentName) {
        String scope = selectedDocumentName != null && !selectedDocumentName.isBlank()
                ? "已选中一份具体文档，名称为《" + selectedDocumentName + "》；用户的文档指代均指向这份文档"
                : "当前未指定单份文档，检索范围是全部可用资料";
        return "当前资料范围：" + scope + "\n用户问题：" + question;
    }

    @Override
    public String generateStreaming(AnswerContext context, Consumer<String> tokenConsumer) {
        StringBuilder answer = new StringBuilder();
        try {
            HttpResponse<Stream<String>> response = httpClient.send(
                    request(context, true).build(), HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (Stream<String> ignored = response.body()) {
                    // 关闭错误响应流，避免占用 HTTP 连接。
                }
                throw new LlmGenerationException("大模型接口返回状态码 " + response.statusCode());
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> consumeEventLine(line, tokenConsumer, answer));
            }
            if (answer.toString().isBlank()) {
                throw new LlmGenerationException("大模型流式响应内容为空或格式不正确");
            }
            return answer.toString().strip();
        } catch (LlmGenerationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmGenerationException("大模型流式请求被中断", exception);
        } catch (Exception exception) {
            throw new LlmGenerationException("大模型流式请求失败", exception);
        }
    }

    private HttpRequest.Builder request(AnswerContext context, boolean streaming) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", properties.model());
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt(context))));
        requestBody.put("temperature", properties.temperature());
        requestBody.put("max_tokens", properties.maxTokens());
        requestBody.put("stream", streaming);
        if (isDeepSeekEndpoint()) {
            // RAG 摘要需要稳定输出最终正文；DeepSeek 默认思考模式可能耗尽 max_tokens，导致 content 为空。
            requestBody.put("thinking", Map.of("type", "disabled"));
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.endpoint()))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", streaming ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));
        if (!properties.apiKey().isBlank()) {
            request.header("Authorization", "Bearer " + properties.apiKey());
        }
        return request;
    }

    private void consumeEventLine(String line, Consumer<String> tokenConsumer, StringBuilder answer) {
        if (line == null || !line.startsWith("data:")) {
            return;
        }
        String data = line.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return;
        }
        try {
            JsonNode content = objectMapper.readTree(data)
                    .path("choices").path(0).path("delta").path("content");
            if (content.isTextual() && !content.asText().isEmpty()) {
                answer.append(content.asText());
                tokenConsumer.accept(content.asText());
            }
        } catch (Exception exception) {
            throw new LlmGenerationException("无法解析大模型流式响应", exception);
        }
    }

    private boolean isDeepSeekEndpoint() {
        String host = URI.create(properties.endpoint()).getHost();
        return host != null && (host.equalsIgnoreCase("deepseek.com")
                || host.toLowerCase().endsWith(".deepseek.com"));
    }

    private String jsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmGenerationException("查询规划没有返回 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    private String nullableText(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private String userPrompt(AnswerContext context) {
        String evidence = context.passages().stream()
                .map(passage -> citationLabel(passage) + " " + passage.content())
                .collect(Collectors.joining("\n\n"));
        String scope = context.selectedDocumentName() == null
                ? "全部可用资料"
                : "《" + context.selectedDocumentName() + "》";
        return "当前资料范围：\n" + scope + "\n\n问题：\n" + context.question()
                + "\n\n证据：\n" + evidence;
    }

    private String citationLabel(ContextPassage passage) {
        return passage.citationNumbers().stream()
                .map(number -> "[" + number + "]")
                .collect(Collectors.joining());
    }
}
