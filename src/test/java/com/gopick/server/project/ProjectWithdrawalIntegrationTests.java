package com.gopick.server.project;

import com.gopick.server.bookmark.domain.ProjectScrap;
import com.gopick.server.project.domain.ProjectAttribute;
import com.gopick.server.project.domain.ProjectAttributeCategory;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectLink;
import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.domain.Recruitment;
import com.gopick.server.project.domain.RecruitmentTechnology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectWithdrawalIntegrationTests extends ProjectApiFixture {
    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"PREPARING", "IN_PROGRESS"})
    void memberCanWithdrawWhileTheProjectAndItsLeaderRemain(ProjectStatus state) throws Exception {
        project.setStatus(state);

        mvc.perform(delete(withdrawalPath()).session(session(member)))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        mvc.perform(get(path()).session(session(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value(state.name()))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].profileId").value(leaderProfile.getId().intValue()));
        mvc.perform(get("/me/projects").session(session(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void preparingLeaderWithdrawalPhysicallyDeletesTheProjectAndItsDependentRows() throws Exception {
        project.getAttributes().add(new ProjectAttribute(ProjectAttributeCategory.FIELD, "WEB"));
        project.updateExperience(ProjectConditionMode.PREFERRED, List.of("HACKATHON"), List.of("WEB"));
        project.getLinks().add(new ProjectLink("GITHUB", "https://example.com/project"));
        var recruitment = new Recruitment(project, ProjectRole.BACKEND, 1,
                ProjectConditionMode.REQUIRED, List.of(new RecruitmentTechnology("Java", 2)));
        project.getRecruitments().add(recruitment);
        persist(new ProjectScrap(outsider, project));
        var other = newProject("other", ProjectStatus.PREPARING, false);
        persist(new ProjectMember(other, leaderProfile, ProjectMemberType.LEADER, ProjectRole.BACKEND));
        em.flush();
        Long projectId = project.getId();
        Long recruitmentId = (Long) em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(recruitment);
        List<String> dependentTables = List.of("project_member", "project_scrap", "project_attribute",
                "project_experience_type", "project_experience_field", "project_link", "recruitment");
        for (String table : dependentTables) {
            assertThat(countRows(table, "project_id", projectId)).as(table).isPositive();
        }
        assertThat(countRows("recruitment_technology", "recruitment_id", recruitmentId)).isEqualTo(1);
        em.clear();

        mvc.perform(delete(withdrawalPath()).session(session(leader)))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertThat(countRows("project", "id", projectId)).isZero();
        for (String table : dependentTables) {
            assertThat(countRows(table, "project_id", projectId)).as(table).isZero();
        }
        assertThat(countRows("recruitment_technology", "recruitment_id", recruitmentId)).isZero();
        assertThat(countRows("project_member", "project_id", other.getId())).isEqualTo(1);
        mvc.perform(get(path()).session(session(leader))).andExpect(status().isNotFound());
    }

    @Test
    void inProgressLeaderMustTransferAuthorityBeforeWithdrawing() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);

        mvc.perform(delete(withdrawalPath()).session(session(leader)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
        mvc.perform(patch(path() + "/leader").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfileId\":" + memberProfile.getId() + "}"))
                .andExpect(status().isNoContent());
        mvc.perform(delete(withdrawalPath()).session(session(leader)))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        mvc.perform(get(path()).session(session(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].profileId").value(memberProfile.getId().intValue()))
                .andExpect(jsonPath("$.members[0].authority").value("LEADER"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completedProjectPreservesTheMembershipHistory(boolean isLeader) throws Exception {
        project.setStatus(ProjectStatus.COMPLETED);

        mvc.perform(delete(withdrawalPath()).session(session(isLeader ? leader : member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));

        assertThat(countRows("project_member", "project_id", project.getId())).isEqualTo(2);
    }

    @Test
    void withdrawalRequiresAValidProjectAndCurrentMembership() throws Exception {
        mvc.perform(delete(withdrawalPath()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(delete(withdrawalPath()).session(session(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        mvc.perform(delete("/projects/{projectId}/members/me", Long.MAX_VALUE).session(session(leader)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(delete("/projects/0/members/me").session(session(leader)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String withdrawalPath() {
        return path() + "/members/me";
    }

    private long countRows(String table, String key, Long id) {
        return (Long) em.createNativeQuery("select count(*) from " + table + " where " + key + " = :id", Long.class)
                .setParameter("id", id).getSingleResult();
    }
}
