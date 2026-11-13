package com.gopick.server.project;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectStage;
import com.gopick.server.project.domain.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectCompletionIntegrationTests extends ProjectApiFixture {
    @Test
    void leaderCanCompleteAnInProgressProjectWithoutChangingItsStage() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setStage(ProjectStage.DESIGN);
        var previousStageUpdatedAt = project.getStageUpdatedAt();
        em.flush();

        mvc.perform(post(path() + "/complete").session(session(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.stage").value("DESIGN"))
                .andExpect(jsonPath("$.recruiting").value(false))
                .andExpect(jsonPath("$.statusUpdatedAt").isString());

        em.flush();
        em.clear();
        Project stored = em.find(Project.class, project.getId());
        assertThat(stored.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(stored.getRecruiting()).isFalse();
        assertThat(stored.getStage()).isEqualTo(ProjectStage.DESIGN);
        assertThat(stored.getStageUpdatedAt()).isCloseTo(previousStageUpdatedAt, within(1, ChronoUnit.MICROS));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void memberAndOutsiderCannotCompleteTheProject(boolean isMember) throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        em.flush();

        mvc.perform(post(path() + "/complete").session(session(isMember ? member : outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"PREPARING", "COMPLETED"})
    void completionRequiresAnInProgressProject(ProjectStatus state) throws Exception {
        project.setStatus(state);
        em.flush();

        mvc.perform(post(path() + "/complete").session(session(leader)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
    }

    @Test
    void completedProjectCannotRestartChangeStageOrReopenRecruitment() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        em.flush();
        var login = session(leader);

        mvc.perform(post(path() + "/complete").session(login)).andExpect(status().isOk());
        mvc.perform(post(path() + "/start").session(login)).andExpect(status().isConflict());
        mvc.perform(patch(path() + "/stage").session(login)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"DESIGN\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(path()).session(login)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recruiting\":true}"))
                .andExpect(status().isConflict());
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(project.getRecruiting()).isFalse();
    }

    @Test
    void completionRequiresLoginAndAnExistingProject() throws Exception {
        mvc.perform(post(path() + "/complete"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/projects/{projectId}/complete", Long.MAX_VALUE).session(session(leader)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }
}
