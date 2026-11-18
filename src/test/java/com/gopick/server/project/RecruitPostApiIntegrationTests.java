package com.gopick.server.project;

import com.gopick.server.project.service.RecruitPostAiClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:recruit-post-api;MODE=MySQL;NON_KEYWORDS=VALUE")
@AutoConfigureMockMvc
@Transactional
class RecruitPostApiIntegrationTests extends ProjectApiFixture {
    private static final String DRAFT_PATH = "/projects/recruit-post/draft";
    private static final String REQUIRED_DRAFT_FIELD = "{\"title\":\"GoPick\"}";
    private static final String TEXT_COLUMN_LIMIT = "가".repeat(21_845);

    @MockitoBean
    private RecruitPostAiClient aiClient;

    @Test
    void noSessionOrAnOlderSessionFromAWithdrawnAccountCannotGenerateDrafts() throws Exception {
        var olderSession = session(outsider);
        mvc.perform(delete("/me").session(session(outsider))).andExpect(status().isNoContent());

        mvc.perform(post(DRAFT_PATH).contentType(MediaType.APPLICATION_JSON).content(REQUIRED_DRAFT_FIELD))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post(DRAFT_PATH).session(olderSession)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUIRED_DRAFT_FIELD))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(aiClient);
    }

    @Test
    void draftsCanUseAnIncompleteProjectFormAndARecruitmentWithZeroRemaining() throws Exception {
        when(aiClient.generate(any())).thenReturn("검토 가능한 모집글");

        mvc.perform(post(DRAFT_PATH).session(session(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"GoPick","description":"","fields":[],"recruitments":[
                          {"role":"BACKEND","remainingCount":0,"techMode":"NONE"}
                        ]}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post").value("검토 가능한 모집글"));
        mvc.perform(get("/projects").session(session(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anEmptyDraftReturnsAValidationErrorOnTheTitle() throws Exception {
        mvc.perform(post(DRAFT_PATH).session(session(leader)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.title").isString());
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"title\":\"GoPick\",\"fields\":[{}]}",
            "{\"title\":\"GoPick\",\"purposes\":[null]}",
            "{\"title\":\"GoPick\",\"recruitments\":[null]}",
            "{\"title\":\"GoPick\",\"recruitments\":[{\"role\":\"BACKEND\",\"remainingCount\":-1,\"techMode\":\"NONE\"}]}",
            """
            {"title":"GoPick","recruitments":[{"role":"BACKEND","remainingCount":1,"techMode":"PREFERRED",
              "technologies":[{"technology":"Java","minLevel":5}]}]}
            """
    })
    void malformedAndInvalidNestedFormFieldsAreRejectedBeforeCallingAi(String draft) throws Exception {
        mvc.perform(post(DRAFT_PATH).session(session(leader)).contentType(MediaType.APPLICATION_JSON).content(draft))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(aiClient);
    }

    @Test
    void oversizedDraftFieldsAndListsAreRejectedBeforeCallingAi() throws Exception {
        String draft = """
                {"title":"%s","fields":[%s"WEB"]}
                """.formatted("x".repeat(256), "\"WEB\",".repeat(20));
        mvc.perform(post(DRAFT_PATH).session(session(leader)).contentType(MediaType.APPLICATION_JSON).content(draft))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.title").isString())
                .andExpect(jsonPath("$.errors.fields").isString());
        verifyNoInteractions(aiClient);
    }

    @Test
    void createAndPatchAcceptPostsAtTheUtf8ColumnLimit() throws Exception {
        String createdResponse = mvc.perform(post("/projects").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"title":"Recruiting","role":"BACKEND","recruiting":true,"post":"%s"}
                        """.formatted(TEXT_COLUMN_LIMIT)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int createdId = JsonPath.read(createdResponse, "$.projectId");
        em.flush();
        em.clear();
        mvc.perform(get("/projects/" + createdId).session(session(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post").value(TEXT_COLUMN_LIMIT));

        mvc.perform(patch(path()).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"post\":\"%s\"}".formatted(TEXT_COLUMN_LIMIT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post").value(TEXT_COLUMN_LIMIT));
        em.flush();
        em.clear();
        mvc.perform(get(path()).session(session(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.post").value(TEXT_COLUMN_LIMIT));
    }

    @Test
    void createAndPatchRejectPostsOneByteOverTheUtf8ColumnLimit() throws Exception {
        String oversized = TEXT_COLUMN_LIMIT + "x";
        mvc.perform(post("/projects").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"title":"Not saved","role":"BACKEND","recruiting":true,"post":"%s"}
                        """.formatted(oversized)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.post").isString());
        mvc.perform(patch(path()).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"title":"Not updated","post":"%s"}
                        """.formatted(oversized)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.post").isString());
        mvc.perform(get("/projects").session(session(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("GoPick"));
    }
}
