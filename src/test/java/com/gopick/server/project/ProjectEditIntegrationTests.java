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
class ProjectEditIntegrationTests extends ProjectApiFixture {
    @Test
    void leaderCanEditAllExistingBasicFieldsInOneRequest() throws Exception {
        mvc.perform(edit("""
                {"title":"Updated","description":"New description","background":"New background",
                 "goal":"New goal","wish":"New wish","extra":"New extra","recruiting":false}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.lifecycleStatus").value("PREPARING"))
                .andExpect(jsonPath("$.recruiting").value(false));
        em.flush(); em.clear();
        var stored = em.find(Project.class, project.getId());
        assertThat(stored.getTitle()).isEqualTo("Updated");
        assertThat(stored.getDescription()).isEqualTo("New description");
        assertThat(stored.getBackground()).isEqualTo("New background");
        assertThat(stored.getGoal()).isEqualTo("New goal");
        assertThat(stored.getWish()).isEqualTo("New wish");
        assertThat(stored.getExtra()).isEqualTo("New extra");
        assertThat(stored.getStage()).isNull();
    }
    @Test
    void omittedFieldsArePreservedAndEmptyOptionalTextCanBeSaved() throws Exception {
        mvc.perform(edit("{\"title\":\"Only title\",\"extra\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("description"))
                .andExpect(jsonPath("$.extra").value(""));
        assertThat(project.getRecruiting()).isTrue();
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void memberOrAnotherProjectsLeaderCannotEdit(boolean isMember) throws Exception {
        var actor = isMember ? member : outsider;
        if (!isMember) {
            var other = newProject("other", ProjectStatus.PREPARING, true);
            persist(new ProjectMember(other, profile(outsider, true), ProjectMemberType.LEADER, ProjectRole.BACKEND));
        }
        mvc.perform(patch(path()).session(session(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Blocked\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        assertThat(project.getTitle()).isEqualTo("GoPick");
    }
    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"IN_PROGRESS", "COMPLETED"})
    void cannotReopenRecruitmentAfterStarting(ProjectStatus state) throws Exception {
        project.setStatus(state); project.setRecruiting(false); em.flush();
        mvc.perform(edit("{\"title\":\"Do not save\",\"recruiting\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
        assertThat(project.getTitle()).isEqualTo("GoPick");
        assertThat(project.getRecruiting()).isFalse();
    }
    @ParameterizedTest
    @ValueSource(strings = {"stage", "status", "lifecycleStatus", "members"})
    void unsupportedFieldsAreRejectedRatherThanSilentlySaved(String field) throws Exception {
        mvc.perform(edit("{\"" + field + "\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(project.getTitle()).isEqualTo("GoPick");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.PREPARING);
    }
    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":\" \"}", "{\"title\":null}", "{\"recruiting\":null}"})
    void invalidInputDoesNotPartiallySave(String body) throws Exception {
        mvc.perform(edit(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(project.getTitle()).isEqualTo("GoPick");
    }
    @Test
    void requiresValidAccountAndExistingProject() throws Exception {
        mvc.perform(patch(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        var stale = new MockHttpSession(); stale.setAttribute("accountId", Long.MAX_VALUE);
        mvc.perform(patch(path()).session(stale).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/projects/9223372036854775807").session(session(leader)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void invalidIdIsValidationError(String id) throws Exception {
        mvc.perform(patch("/projects/" + id).session(session(leader)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.projectId").isString());
    }
    private MockHttpServletRequestBuilder edit(String body) {
        return patch(path()).session(session(leader)).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
