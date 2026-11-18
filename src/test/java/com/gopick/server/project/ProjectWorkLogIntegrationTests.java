package com.gopick.server.project;

import com.gopick.server.project.domain.ProjectStage;
import com.gopick.server.project.domain.ProjectStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectWorkLogIntegrationTests extends ProjectApiFixture {
    @ParameterizedTest
    @EnumSource(ProjectStage.class)
    void tasksArePersistedPerStageAndCanBeCompletedReopenedAndDeleted(ProjectStage stage) throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        String base = path() + "/stages/" + stage + "/tasks";
        String response = mvc.perform(post(base).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  세부 과제  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("세부 과제"))
                .andExpect(jsonPath("$.stage").value(stage.name())).andExpect(jsonPath("$.completed").value(false))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(response, "$.id");
        em.flush(); em.clear();
        mvc.perform(get(base).session(session(leader))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        var other = stage == ProjectStage.PLANNING ? ProjectStage.DESIGN : ProjectStage.PLANNING;
        mvc.perform(get(path() + "/stages/" + other + "/tasks").session(session(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(patch(base + "/" + id).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
        em.flush(); em.clear();
        mvc.perform(get(base).session(session(member))).andExpect(jsonPath("$[0].completed").value(true));
        mvc.perform(patch(base + "/" + id).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false));
        mvc.perform(delete(base + "/" + id).session(session(member))).andExpect(status().isNoContent());
        mvc.perform(get(base).session(session(leader))).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void activitiesHaveAuthorAndTimestampInNewestFirstOrderAndSurviveAuthorWithdrawal() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        String base = path() + "/activities";
        mvc.perform(post(base).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"first\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("leader")).andExpect(jsonPath("$.createdAt").isString());
        mvc.perform(post(base).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\" second \"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("member"));
        em.flush(); em.clear();
        mvc.perform(get(base).session(session(leader))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].content", contains("second", "first")))
                .andExpect(jsonPath("$[*].authorName", contains("member", "leader")));
        mvc.perform(delete("/me").session(session(member))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get(base).session(session(leader))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorName").value("탈퇴한 회원입니다"))
                .andExpect(jsonPath("$[0].content").value("second"));
    }

    @Test
    void onlyCurrentMembersCanAccessAndCompletedProjectsAreReadOnly() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        String tasks = path() + "/stages/PLANNING/tasks";
        String activities = path() + "/activities";
        mvc.perform(get(tasks)).andExpect(status().isUnauthorized());
        mvc.perform(get(activities).session(session(outsider))).andExpect(status().isForbidden());
        mvc.perform(post(tasks).session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post(activities).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"memo\"}")).andExpect(status().isCreated());
        String taskResponse = mvc.perform(post(tasks).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"task\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int taskId = JsonPath.read(taskResponse, "$.id");
        project.setStatus(ProjectStatus.COMPLETED); em.flush();
        mvc.perform(get(activities).session(session(leader))).andExpect(status().isOk());
        mvc.perform(get(tasks).session(session(member))).andExpect(status().isOk());
        mvc.perform(post(tasks).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}")).andExpect(status().isConflict());
        mvc.perform(post(activities).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"x\"}")).andExpect(status().isConflict());
        mvc.perform(patch(tasks + "/" + taskId).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}")).andExpect(status().isConflict());
        mvc.perform(delete(tasks + "/" + taskId).session(session(member))).andExpect(status().isConflict());
    }

    @Test
    void deletedProjectsDoNotExposeSavedTasksOrActivities() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        mvc.perform(post(path() + "/stages/PLANNING/tasks").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"task\"}")).andExpect(status().isCreated());
        mvc.perform(post(path() + "/activities").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"memo\"}")).andExpect(status().isCreated());
        mvc.perform(delete(path()).session(session(leader))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get(path() + "/stages/PLANNING/tasks").session(session(member))).andExpect(status().isNotFound());
        mvc.perform(get(path() + "/activities").session(session(member))).andExpect(status().isNotFound());
    }

    @Test
    void validatesInputsAndPreventsCrossStageOrCrossProjectChanges() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        String base = path() + "/stages/PLANNING/tasks";
        String response = mvc.perform(post(base).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"task\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(response, "$.id");
        mvc.perform(delete(path() + "/stages/DESIGN/tasks/" + id).session(session(leader))).andExpect(status().isNotFound());
        var another = newProject("another", ProjectStatus.IN_PROGRESS, true);
        persist(new com.gopick.server.project.domain.ProjectMember(another, leaderProfile,
                com.gopick.server.project.domain.ProjectMemberType.LEADER, com.gopick.server.project.domain.ProjectRole.BACKEND));
        mvc.perform(delete("/projects/" + another.getId() + "/stages/PLANNING/tasks/" + id).session(session(leader)))
                .andExpect(status().isNotFound());
        mvc.perform(get(path() + "/stages/UNKNOWN/tasks").session(session(leader))).andExpect(status().isBadRequest());
        mvc.perform(patch(base + "/bad").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}")).andExpect(status().isBadRequest());
        mvc.perform(patch(base + "/" + id).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(base).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post(path() + "/activities").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"a\\nb\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void preparingLeaderWithdrawalDeletesAssociatedRows() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        mvc.perform(post(path() + "/stages/PLANNING/tasks").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"task\"}")).andExpect(status().isCreated());
        mvc.perform(post(path() + "/activities").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"memo\"}")).andExpect(status().isCreated());
        project.setStatus(ProjectStatus.PREPARING); em.flush();
        mvc.perform(delete(path() + "/members/me").session(session(leader))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        for (String table : new String[]{"project_stage_task", "project_activity"}) {
            Number count = (Number) em.createNativeQuery("select count(*) from " + table + " where project_id = :id")
                    .setParameter("id", project.getId()).getSingleResult();
            assertThat(count.longValue()).isZero();
        }
    }
}
