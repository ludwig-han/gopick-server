package com.gopick.server.project;

import com.gopick.server.project.domain.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectWorkLogIntegrationTests extends ProjectApiFixture {
    @ParameterizedTest
    @EnumSource(ProjectStage.class)
    void tasksArePersistedForEveryStageAndCanBeCompletedReopenedAndDeleted(ProjectStage stage) throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        String base = path() + "/tasks";
        String response = mvc.perform(post(base).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"" + stage + "\",\"text\":\"  세부 과제  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("세부 과제"))
                .andExpect(jsonPath("$.stage").value(stage.name())).andExpect(jsonPath("$.done").value(false))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(response, "$.id");
        em.flush(); em.clear();
        mvc.perform(get(path()).session(session(outsider))).andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[" + stage.ordinal() + "][0].id").value(id));
        mvc.perform(get(base).session(session(outsider))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(patch(base + "/" + id).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.done").value(true));
        em.flush(); em.clear();
        mvc.perform(get(base).session(session(member))).andExpect(jsonPath("$[0].done").value(true));
        mvc.perform(patch(base + "/" + id).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.done").value(false));
        mvc.perform(delete(base + "/" + id).session(session(member))).andExpect(status().isNoContent());
        mvc.perform(get(base).session(session(leader))).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void detailContainsFiveGroupsInStageOrderAndTasksInCreationOrder() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        for (String text : new String[]{"first", "second"}) {
            mvc.perform(post(path() + "/tasks").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"stage\":\"DESIGN\",\"text\":\"" + text + "\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get(path()).session(session(outsider))).andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(5))
                .andExpect(jsonPath("$.tasks[0]").isEmpty())
                .andExpect(jsonPath("$.tasks[1][*].text", contains("first", "second")))
                .andExpect(jsonPath("$.tasks[2]").isEmpty())
                .andExpect(jsonPath("$.tasks[3]").isEmpty())
                .andExpect(jsonPath("$.tasks[4]").isEmpty())
                .andExpect(jsonPath("$.log").isEmpty());
    }

    @Test
    void activitiesHaveServerAuthorDateAndNewestFirstOrderAndSurviveAuthorWithdrawal() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        String base = path() + "/activities";
        mvc.perform(post(base).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"first\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("leader")).andExpect(jsonPath("$.date").isString());
        mvc.perform(post(base).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\" second \"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("member"));
        em.flush(); em.clear();
        mvc.perform(get(path()).session(session(outsider))).andExpect(status().isOk())
                .andExpect(jsonPath("$.log[*].text", contains("second", "first")))
                .andExpect(jsonPath("$.log[*].author", contains("member", "leader")));
        mvc.perform(get(base).session(session(outsider))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].text", contains("second", "first")));
        mvc.perform(delete("/me").session(session(member))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get(path()).session(session(leader))).andExpect(status().isOk())
                .andExpect(jsonPath("$.log[0].author").value("탈퇴한 회원입니다"))
                .andExpect(jsonPath("$.log[0].text").value("second"));
    }

    @Test
    void allLoggedInUsersCanReadButOnlyMembersCanWriteAndCompletedProjectsAreReadOnly() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        String tasks = path() + "/tasks";
        String activities = path() + "/activities";
        mvc.perform(get(tasks)).andExpect(status().isUnauthorized());
        mvc.perform(get(activities).session(session(outsider))).andExpect(status().isOk());
        mvc.perform(get(tasks).session(session(outsider))).andExpect(status().isOk());
        mvc.perform(post(tasks).session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"PLANNING\",\"text\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post(activities).session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"memo\"}")).andExpect(status().isForbidden());
        mvc.perform(post(activities).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"memo\"}")).andExpect(status().isCreated());
        String taskResponse = mvc.perform(post(tasks).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"PLANNING\",\"text\":\"task\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int taskId = JsonPath.read(taskResponse, "$.id");
        mvc.perform(patch(tasks + "/" + taskId).session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":true}")).andExpect(status().isForbidden());
        mvc.perform(delete(tasks + "/" + taskId).session(session(outsider))).andExpect(status().isForbidden());
        project.setStatus(ProjectStatus.COMPLETED); em.flush();
        mvc.perform(get(path()).session(session(outsider))).andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0][0].text").value("task")).andExpect(jsonPath("$.log[0].text").value("memo"));
        mvc.perform(post(tasks).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"PLANNING\",\"text\":\"x\"}")).andExpect(status().isConflict());
        mvc.perform(post(activities).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"x\"}")).andExpect(status().isConflict());
        mvc.perform(patch(tasks + "/" + taskId).session(session(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":true}")).andExpect(status().isConflict());
        mvc.perform(delete(tasks + "/" + taskId).session(session(member))).andExpect(status().isConflict());
    }

    @Test
    void validatesInputsAndPreventsCrossProjectChanges() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        String base = path() + "/tasks";
        String response = mvc.perform(post(base).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"PLANNING\",\"text\":\"task\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(response, "$.id");
        var another = newProject("another", ProjectStatus.IN_PROGRESS, true);
        persist(new ProjectMember(another, leaderProfile, ProjectMemberType.LEADER, ProjectRole.BACKEND));
        mvc.perform(delete("/projects/" + another.getId() + "/tasks/" + id).session(session(leader))).andExpect(status().isNotFound());
        mvc.perform(post(base).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"UNKNOWN\",\"text\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(base).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch(base + "/bad").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"done\":true}")).andExpect(status().isBadRequest());
        mvc.perform(patch(base + "/" + id).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(base).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"DESIGN\",\"text\":\" \"}")).andExpect(status().isBadRequest());
        String activity = path() + "/activities";
        mvc.perform(post(activity).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"a\\nb\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(activity).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + "a".repeat(100) + "\"}")).andExpect(status().isCreated());
        mvc.perform(post(activity).session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + "a".repeat(101) + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void deletedProjectsDoNotExposeSavedTasksOrActivities() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        mvc.perform(post(path() + "/tasks").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"PLANNING\",\"text\":\"task\"}")).andExpect(status().isCreated());
        mvc.perform(post(path() + "/activities").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"memo\"}")).andExpect(status().isCreated());
        mvc.perform(delete(path()).session(session(leader))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get(path()).session(session(outsider))).andExpect(status().isNotFound());
        mvc.perform(get(path() + "/tasks").session(session(member))).andExpect(status().isNotFound());
        mvc.perform(get(path() + "/activities").session(session(member))).andExpect(status().isNotFound());
    }

    @Test
    void preparingLeaderWithdrawalDeletesAssociatedRows() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        mvc.perform(post(path() + "/tasks").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"PLANNING\",\"text\":\"task\"}")).andExpect(status().isCreated());
        mvc.perform(post(path() + "/activities").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"memo\"}")).andExpect(status().isCreated());
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
