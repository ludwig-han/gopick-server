package com.gopick.server.project;

import com.gopick.server.project.domain.ProjectAttribute;
import com.gopick.server.project.domain.ProjectAttributeCategory;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.domain.Recruitment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectDeletionIntegrationTests extends ProjectApiFixture {
    @ParameterizedTest
    @EnumSource(ProjectStatus.class)
    void leaderCanSoftDeleteAProjectAtAnyLifecycleStage(ProjectStatus projectStatus) throws Exception {
        project.setStatus(projectStatus);
        var login = session(leader);

        mvc.perform(delete(path()).session(login)).andExpect(status().isNoContent());

        em.flush();
        em.clear();
        var stored = em.createNativeQuery("select deleted_at, status from project where id = :projectId")
                .setParameter("projectId", project.getId())
                .getSingleResult();
        assertThat((Object[]) stored).doesNotContainNull();
        assertThat(((Object[]) stored)[1]).isEqualTo(projectStatus.name());

        mvc.perform(delete(path()).session(login))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void softDeletedProjectIsHiddenWithoutRemovingRelatedRecords() throws Exception {
        var visible = newProject("Visible", ProjectStatus.PREPARING, true);
        persist(new ProjectMember(visible, leaderProfile, ProjectMemberType.LEADER, ProjectRole.BACKEND));
        project.getAttributes().add(new ProjectAttribute(ProjectAttributeCategory.FIELD, "WEB"));
        persist(new Recruitment(project, ProjectRole.DESIGN, 1, ProjectConditionMode.NONE, List.of()));
        var leaderLogin = session(leader);
        var outsiderLogin = session(outsider);
        String deletedProjectScrap = path() + "/scraps";

        mvc.perform(post(deletedProjectScrap).session(outsiderLogin)).andExpect(status().isNoContent());
        mvc.perform(post("/projects/{projectId}/scraps", visible.getId()).session(outsiderLogin))
                .andExpect(status().isNoContent());
        mvc.perform(delete(path()).session(leaderLogin)).andExpect(status().isNoContent());

        em.flush();
        em.clear();

        mvc.perform(get("/projects").session(outsiderLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].projectId", contains(visible.getId().intValue())));
        mvc.perform(get("/me/projects").session(leaderLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].projectId", contains(visible.getId().intValue())));
        mvc.perform(get("/me/scrapped-projects").session(outsiderLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].projectId", contains(visible.getId().intValue())));
        mvc.perform(get(path()).session(leaderLogin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(post(path() + "/start").session(leaderLogin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(post(deletedProjectScrap).session(outsiderLogin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

        assertThat(relatedRows("project_member")).isEqualTo(2);
        assertThat(relatedRows("project_attribute")).isEqualTo(1);
        assertThat(relatedRows("recruitment")).isEqualTo(1);
        assertThat(relatedRows("project_scrap")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void memberAndOutsiderCannotDeleteAProject(boolean isMember) throws Exception {
        mvc.perform(delete(path()).session(session(isMember ? member : outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));

        mvc.perform(get(path()).session(session(leader))).andExpect(status().isOk());
    }

    @Test
    void deletionRequiresAuthenticationAndAnExistingProject() throws Exception {
        mvc.perform(delete(path()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(delete("/projects/{projectId}", Long.MAX_VALUE).session(session(leader)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    private long relatedRows(String table) {
        return ((Number) em.createNativeQuery("select count(*) from " + table + " where project_id = :projectId")
                .setParameter("projectId", project.getId())
                .getSingleResult()).longValue();
    }
}
