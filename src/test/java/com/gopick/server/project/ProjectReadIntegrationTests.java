package com.gopick.server.project;

import com.gopick.server.project.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectReadIntegrationTests extends ProjectApiFixture {
    @Test
    void preparingAndRecruitingFiltersAreCombined() throws Exception {
        newProject("closed", ProjectStatus.PREPARING, false);
        newProject("started", ProjectStatus.IN_PROGRESS, false);
        mvc.perform(get("/projects").param("status", "PREPARING").param("recruiting", "true").session(session(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectId").value(project.getId().intValue()))
                .andExpect(jsonPath("$[0].lifecycleStatus").value("PREPARING"));
    }
    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"IN_PROGRESS", "COMPLETED"})
    void filtersProgressAndCompletedTabs(ProjectStatus state) throws Exception {
        var target = newProject("matching", state, false);
        mvc.perform(get("/projects").param("status", state.name()).session(session(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].projectId").value(target.getId().intValue()));
    }
    @Test
    void optionalFiltersAndOrderingWork() throws Exception {
        var second = newProject("second", ProjectStatus.PREPARING, false);
        var third = newProject("third", ProjectStatus.COMPLETED, false);
        mvc.perform(get("/projects").session(session(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].projectId", contains(
                        third.getId().intValue(), second.getId().intValue(), project.getId().intValue())));
        mvc.perform(get("/projects").param("recruiting", "false").session(session(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/projects").param("status", "COMPLETED").param("recruiting", "true").session(session(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
    @Test
    void detailReturnsBasicInfoAndOnlyCurrentProjectMembersWithoutPrivateData() throws Exception {
        var other = newProject("unrelated", ProjectStatus.PREPARING, false);
        persist(new ProjectMember(other, profile(outsider, true), ProjectMemberType.LEADER, ProjectRole.AI));
        em.flush(); em.clear();
        var result = mvc.perform(get(path()).session(session(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("GoPick"))
                .andExpect(jsonPath("$.description").value("description"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].authority").value("LEADER"))
                .andExpect(jsonPath("$.members[0].role").value("BACKEND"))
                .andExpect(jsonPath("$.members[1].authority").value("MEMBER"))
                .andExpect(jsonPath("$.members[1].name").isEmpty())
                .andExpect(jsonPath("$.members[1].isPublic").value(false)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("encoded-secret", "@example.com", "contact-secret", "unrelated");
        mvc.perform(get(path()).session(session(member))).andExpect(status().isOk())
                .andExpect(jsonPath("$.members[1].name").value("member"));
    }
    @ParameterizedTest
    @CsvSource({"status,WRONG", "recruiting,maybe"})
    void invalidFiltersAreValidationErrors(String field, String value) throws Exception {
        mvc.perform(get("/projects").param(field, value).session(session(leader)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors." + field).isString());
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidIdIsValidationError(String id) throws Exception {
        mvc.perform(get("/projects/" + id).session(session(leader)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.projectId").isString());
    }
    @Test
    void missingProjectReturnsNotFound() throws Exception {
        mvc.perform(get("/projects/9223372036854775807").session(session(leader)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }
    @Test
    void bothReadEndpointsRequireValidSession() throws Exception {
        for (boolean stale : new boolean[]{false, true}) {
            for (String path : new String[]{"/projects", path()}) {
                var request = get(path);
                if (stale) { var session = new MockHttpSession(); session.setAttribute("accountId", Long.MAX_VALUE); request.session(session); }
                mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            }
        }
    }
}
