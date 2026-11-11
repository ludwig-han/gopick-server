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
class ProjectProgressIntegrationTests extends ProjectApiFixture {
    @Test
    void leaderStartsProjectAndClosesRecruitmentWithoutChangingUnspecifiedStage() throws Exception {
        mvc.perform(post(path() + "/start").session(session(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycleStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.recruiting").value(false)).andExpect(jsonPath("$.stage").isEmpty());
        em.flush(); em.clear();
        var stored = em.find(Project.class, project.getId());
        assertThat(stored.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(stored.getRecruiting()).isFalse();
        mvc.perform(post(path() + "/start").session(session(leader)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onlyLeaderCanStart(boolean isMember) throws Exception {
        mvc.perform(post(path() + "/start").session(session(isMember ? member : outsider)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.PREPARING);
        assertThat(project.getRecruiting()).isTrue();
    }
    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"IN_PROGRESS", "COMPLETED"})
    void startedOrCompletedProjectCannotStartAgain(ProjectStatus state) throws Exception {
        project.setStatus(state); project.setRecruiting(false); em.flush();
        mvc.perform(post(path() + "/start").session(session(leader)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void leaderAndMemberCanChangeStageWithoutCompletingProject(boolean isLeader) throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS); project.setRecruiting(false); em.flush();
        mvc.perform(stage("{\"stage\":\"PRODUCTION\"}").session(session(isLeader ? leader : member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("PRODUCTION"))
                .andExpect(jsonPath("$.lifecycleStatus").value("IN_PROGRESS"));
        assertThat(project.getStageUpdatedAt()).isNotNull();
    }
    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"PREPARING", "COMPLETED"})
    void stageChangesRequireInProgress(ProjectStatus state) throws Exception {
        project.setStatus(state); em.flush();
        mvc.perform(stage("{\"stage\":\"DESIGN\"}").session(session(leader)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
    }
    @Test
    void outsiderCannotChangeStage() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS); em.flush();
        mvc.perform(stage("{\"stage\":\"DESIGN\"}").session(session(outsider)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"stage\":null}", "{\"stage\":\"UNKNOWN\"}"})
    void stageMustBeValid(String body) throws Exception {
        mvc.perform(stage(body).session(session(leader))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test
    void bothActionsRequireValidAccountSessionAndExistingProject() throws Exception {
        for (var request : new MockHttpServletRequestBuilder[]{post(path() + "/start"), stage("{\"stage\":\"DESIGN\"}")})
            mvc.perform(request).andExpect(status().isUnauthorized());
        for (var request : new MockHttpServletRequestBuilder[]{post(path() + "/start"), stage("{\"stage\":\"DESIGN\"}")}) {
            var stale = new MockHttpSession(); stale.setAttribute("accountId", Long.MAX_VALUE);
            mvc.perform(request.session(stale)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/projects/9223372036854775807/start").session(session(leader)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(patch("/projects/9223372036854775807/stage").session(session(leader))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"DESIGN\"}"))
                .andExpect(status().isNotFound());
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void bothActionsValidateProjectId(String id) throws Exception {
        mvc.perform(post("/projects/" + id + "/start").session(session(leader)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.projectId").isString());
        mvc.perform(patch("/projects/" + id + "/stage").session(session(leader))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"DESIGN\"}"))
                .andExpect(status().isBadRequest());
    }
    private MockHttpServletRequestBuilder stage(String body) {
        return patch(path() + "/stage").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
