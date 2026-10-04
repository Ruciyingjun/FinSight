/**
 * 问答接口校验测试：验证空问题和超出范围的引用数量会返回统一参数错误。
 */
package com.financialresearch.question;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class QuestionControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsBlankQuestionAndExcessiveLimit() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"  ","limit":11}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.question").value("问题不能为空"))
                .andExpect(jsonPath("$.details.limit").value("limit 不能大于 10"));

        mockMvc.perform(post("/api/v1/questions/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"  ","limit":11}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void streamsContextTokensAndFinalAnswerAsSseEvents() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"测试问题","limit":3}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:context")))
                .andExpect(content().string(containsString("event:token")))
                .andExpect(content().string(containsString("event:complete")))
                .andExpect(content().string(containsString("LOCAL_FALLBACK")));
    }

    @Test
    void doesNotFabricateEvidenceWhenPlannerIsDisabledAndNoDocumentMatches() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"你好","limit":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerSource").value("LOCAL_FALLBACK"))
                .andExpect(jsonPath("$.evidenceFound").value(false))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("未在当前资料库")));
    }
}
