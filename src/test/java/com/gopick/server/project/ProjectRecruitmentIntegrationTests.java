package com.gopick.server.project;

import com.gopick.server.project.domain.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectRecruitmentIntegrationTests extends ProjectApiFixture {
    @Test
    void creationStoresRecruitmentDetailsAndReturnsThemThroughProjectDetail() throws Exception {
        var result = mvc.perform(post("/projects").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Recruitment test", "recruiting": true, "role": "PM",
                                  "attributes": [
                                    {"category": "FIELD", "value": "WEB"},
                                    {"category": "PURPOSE", "value": "학습"},
                                    {"category": "COLLAB", "value": "코드 리뷰"}
                                  ],
                                  "experience": {"mode": "PREFERRED", "types": ["HACKATHON"], "fields": ["WEB"]},
                                  "recruitments": [
                                    {"role": "BACKEND", "remainingCount": 2, "techMode": "REQUIRED",
                                     "technologies": [{"technology": "Java", "minLevel": 3}, {"technology": "SQL", "minLevel": 2}]}
                                  ],
                                  "links": [{"type": "GitHub", "url": "https://github.com/example/project"}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").isNumber())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.projectId").toString();
        em.flush();
        em.clear();

        detail("/projects/" + id)
                .andExpect(jsonPath("$.title").value("Recruitment test"))
                .andExpect(jsonPath("$.lifecycleStatus").value("PREPARING"))
                .andExpect(jsonPath("$.members[0].authority").value("LEADER"))
                .andExpect(jsonPath("$.members[0].role").value("PM"))
                .andExpect(jsonPath("$.attributes", hasSize(3)))
                .andExpect(jsonPath("$.attributes[0].category").value("FIELD"))
                .andExpect(jsonPath("$.attributes[0].value").value("WEB"))
                .andExpect(jsonPath("$.experience.mode").value("PREFERRED"))
                .andExpect(jsonPath("$.experience.types[0]").value("HACKATHON"))
                .andExpect(jsonPath("$.experience.fields[0]").value("WEB"))
                .andExpect(jsonPath("$.recruitments[0].role").value("BACKEND"))
                .andExpect(jsonPath("$.recruitments[0].remainingCount").value(2))
                .andExpect(jsonPath("$.recruitments[0].techMode").value("REQUIRED"))
                .andExpect(jsonPath("$.recruitments[0].technologies[0].technology").value("Java"))
                .andExpect(jsonPath("$.recruitments[0].technologies[0].minLevel").value(3))
                .andExpect(jsonPath("$.recruitments[0].technologies[1].minLevel").value(2))
                .andExpect(jsonPath("$.links[0].type").value("GitHub"))
                .andExpect(jsonPath("$.links[0].url").value("https://github.com/example/project"));
    }

    @Test
    void legacyProjectsAndCreateRequestsWithoutRecruitmentDetailsStillWork() throws Exception {
        detail(path())
                .andExpect(jsonPath("$.attributes").isEmpty())
                .andExpect(jsonPath("$.experience.mode").value("NONE"))
                .andExpect(jsonPath("$.recruitments").isEmpty())
                .andExpect(jsonPath("$.links").isEmpty());
        mvc.perform(post("/projects").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Legacy request\",\"role\":\"BACKEND\",\"recruiting\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").isNumber());
    }

    @Test
    void leaderCanReplaceCollectionsAndOmittedFieldsArePreserved() throws Exception {
        mvc.perform(edit("""
                        {
                          "attributes": [{"category": "FIELD", "value": "AI"}],
                          "experience": {"mode": "REQUIRED", "fields": ["AI"]},
                          "recruitments": [
                            {"role": "BACKEND", "remainingCount": 2, "techMode": "PREFERRED",
                             "technologies": [{"technology": "Java", "minLevel": 2}]},
                            {"role": "DESIGN", "remainingCount": 1, "techMode": "NONE"}
                          ],
                          "links": [{"type": "기획안", "url": "https://example.org/plan"}]
                        }
                        """).session(session(leader))).andExpect(status().isOk());
        em.flush();
        em.clear();

        mvc.perform(edit("""
                        {
                          "title": "Updated project",
                          "recruiting": false,
                          "recruitments": [
                            {"role": "BACKEND", "remainingCount": 0, "techMode": "REQUIRED",
                             "technologies": [{"technology": "SQL", "minLevel": 4}]}
                          ],
                          "links": []
                        }
                        """).session(session(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated project"));
        em.flush();
        em.clear();

        detail(path())
                .andExpect(jsonPath("$.recruiting").value(false))
                .andExpect(jsonPath("$.attributes[0].value").value("AI"))
                .andExpect(jsonPath("$.experience.mode").value("REQUIRED"))
                .andExpect(jsonPath("$.experience.fields[0]").value("AI"))
                .andExpect(jsonPath("$.recruitments", hasSize(1)))
                .andExpect(jsonPath("$.recruitments[0].remainingCount").value(0))
                .andExpect(jsonPath("$.recruitments[0].technologies", hasSize(1)))
                .andExpect(jsonPath("$.recruitments[0].technologies[0].technology").value("SQL"))
                .andExpect(jsonPath("$.links").isEmpty());

        mvc.perform(edit("{\"attributes\":[],\"experience\":{\"mode\":\"NONE\"},\"recruitments\":[]}")
                        .session(session(leader))).andExpect(status().isOk());
        em.flush();
        em.clear();
        detail(path())
                .andExpect(jsonPath("$.attributes").isEmpty())
                .andExpect(jsonPath("$.experience.fields").isEmpty())
                .andExpect(jsonPath("$.recruitments").isEmpty());
    }

    @Test
    void memberCannotChangeRecruitmentDetails() throws Exception {
        mvc.perform(edit("{\"links\":[{\"type\":\"기획안\",\"url\":\"https://example.org/plan\"}]}")
                        .session(session(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        detail(path()).andExpect(jsonPath("$.links").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"links\": [{\"type\":\"기획안\",\"url\":\"javascript:alert(1)\"}]",
            "\"attributes\": [{\"category\":\"FIELD\",\"value\":\" \"}]",
            "\"experience\": {\"mode\":\"NONE\",\"fields\":[\"AI\"]}",
            "\"recruitments\": [{\"role\":\"BACKEND\",\"remainingCount\":1,\"techMode\":\"REQUIRED\"}]",
            "\"recruitments\": [{\"role\":\"BACKEND\",\"remainingCount\":-1,\"techMode\":\"NONE\"}]",
            "\"recruitments\": [{\"role\":\"BACKEND\",\"remainingCount\":1,\"techMode\":\"REQUIRED\",\"technologies\":[{\"technology\":\"Java\",\"minLevel\":5}]}",
            "\"recruitments\": [{\"role\":\"BACKEND\",\"remainingCount\":1,\"techMode\":\"NONE\"},{\"role\":\"BACKEND\",\"remainingCount\":1,\"techMode\":\"NONE\"}]",
            "\"links\": null"
    })
    void invalidDetailsRejectTheWholeUpdate(String field) throws Exception {
        mvc.perform(edit("{\"title\":\"Do not save\"," + field + "}").session(session(leader)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        detail(path()).andExpect(jsonPath("$.title").value("GoPick"));
    }

    @Test
    void invalidCreateRequestReturnsValidationError() throws Exception {
        mvc.perform(post("/projects").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Invalid project","recruiting":true,"role":"PM",
                                 "recruitments":[{"role":"BACKEND","remainingCount":2,"techMode":"REQUIRED"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.projectId").doesNotExist());
    }

    @Test
    void recruitmentDetailsDoNotAllowReopeningAnInProgressProject() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setRecruiting(false);
        em.flush();
        mvc.perform(edit("{\"recruiting\":true,\"links\":[{\"type\":\"기획안\",\"url\":\"https://example.org\"}]}")
                        .session(session(leader)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
        detail(path()).andExpect(jsonPath("$.links").isEmpty());
    }

    private MockHttpServletRequestBuilder edit(String body) {
        return patch(path()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions detail(String path) throws Exception {
        return mvc.perform(get(path).session(session(leader))).andExpect(status().isOk());
    }
}
