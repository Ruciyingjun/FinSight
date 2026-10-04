/**
 * 文档接口集成测试：覆盖上传、PDF 解析、查询、删除、输入校验和文件名安全处理。
 */
package com.financialresearch.document;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialresearch.document.chunk.DocumentChunkRepository;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository repository;

    @Autowired
    private DocumentPageRepository pageRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearDocuments() {
        chunkRepository.deleteAll();
        pageRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void uploadsListsGetsAndDeletesDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "quarterly-report.pdf", "application/pdf", "financial content".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/documents/.+")))
                .andExpect(jsonPath("$.filename").value("quarterly-report.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.sizeBytes").value(17))
                .andReturn();

        JsonNode uploaded = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String id = uploaded.get("id").asText();

        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(get("/api/v1/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256", matchesPattern("^[0-9a-f]{64}$")));

        mockMvc.perform(get("/api/v1/documents/{id}/file", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("inline")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("quarterly-report.pdf")))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes("financial content".getBytes()));

        mockMvc.perform(delete("/api/v1/documents/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void rejectsEmptyAndUnsupportedFiles() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        mockMvc.perform(multipart("/api/v1/documents").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));

        MockMultipartFile executable = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "data".getBytes());
        mockMvc.perform(multipart("/api/v1/documents").file(executable))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
    }

    @Test
    void stripsPathComponentsFromUploadedFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../report.pdf", "application/pdf", "safe".getBytes());

        mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("report.pdf"));
    }

    @Test
    void parsesPdfIntoOrderedPagesAndAdvancesStatus() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "research.pdf", "application/pdf", createPdf("First page", "Second page"));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/documents/{id}/process", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        awaitStatus(id, DocumentStatus.READY);

        mockMvc.perform(get("/api/v1/documents/{id}/pages", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].pageNumber").value(1))
                .andExpect(jsonPath("$[0].content").value("First page"))
                .andExpect(jsonPath("$[1].pageNumber").value(2))
                .andExpect(jsonPath("$[1].content").value("Second page"));

        mockMvc.perform(get("/api/v1/documents/{id}/pages/{pageNumber}", id, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(id))
                .andExpect(jsonPath("$.pageNumber").value(2))
                .andExpect(jsonPath("$.content").value("Second page"))
                .andExpect(jsonPath("$.characterCount").value(11));

        mockMvc.perform(get("/api/v1/documents/{id}/pages/{pageNumber}", id, 99))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("文档第 99 页不存在或尚未完成解析"));

        MvcResult highlightedResult = mockMvc.perform(get("/api/v1/documents/{id}/file/highlight", id)
                        .param("page", "2")
                        .param("start", "0")
                        .param("end", "11"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        try (PDDocument highlighted = Loader.loadPDF(highlightedResult.getResponse().getContentAsByteArray())) {
            assertThat(highlighted.getPage(1).getAnnotations())
                    .anyMatch(PDAnnotationHighlight.class::isInstance);
        }

        mockMvc.perform(get("/api/v1/documents/{id}/chunks", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].chunkIndex").value(0))
                .andExpect(jsonPath("$[0].pageNumber").value(1))
                .andExpect(jsonPath("$[0].startOffset").value(0))
                .andExpect(jsonPath("$[0].content").value("First page"))
                .andExpect(jsonPath("$[1].chunkIndex").value(1))
                .andExpect(jsonPath("$[1].pageNumber").value(2));

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "First")
                        .param("documentId", id)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].documentId").value(id))
                .andExpect(jsonPath("$[0].pageNumber").value(1))
                .andExpect(jsonPath("$[0].content").value("First page"))
                .andExpect(jsonPath("$[0].score").isNumber());

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "Second")
                        .param("documentId", id)
                        .param("mode", "VECTOR")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pageNumber").value(2))
                .andExpect(jsonPath("$[0].content").value("Second page"));

        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "question", "First",
                                "documentId", id,
                                "limit", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("First"))
                .andExpect(jsonPath("$.evidenceFound").value(true))
                .andExpect(jsonPath("$.answer").value("[1] First page"))
                .andExpect(jsonPath("$.answerSource").value("LOCAL_FALLBACK"))
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].number").value(1))
                .andExpect(jsonPath("$.citations[0].documentId").value(id))
                .andExpect(jsonPath("$.citations[0].pageNumber").value(1))
                .andExpect(jsonPath("$.citations[0].quote").value("First page"));
    }

    @Test
    void recordsFailureForCorruptedOfficeDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not an office document".getBytes(UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/documents/{id}/process", id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        awaitStatus(id, DocumentStatus.FAILED);
        mockMvc.perform(get("/api/v1/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").isNotEmpty());
    }

    private void awaitStatus(String id, DocumentStatus expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            DocumentStatus current = repository.findById(id).orElseThrow().getStatus();
            if (current == expected) {
                return;
            }
            Thread.sleep(25);
        }
        DocumentEntity document = repository.findById(id).orElseThrow();
        throw new AssertionError("等待文档状态 " + expected + " 超时，当前状态为 "
                + document.getStatus() + "，失败原因：" + document.getFailureReason());
    }

    private byte[] createPdf(String... pageTexts) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(pageText);
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
