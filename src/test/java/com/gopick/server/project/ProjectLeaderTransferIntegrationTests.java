package com.gopick.server.project;

import com.gopick.server.account.domain.Account;
import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectLeaderTransferIntegrationTests extends ProjectApiFixture {
    @Test
    void transferLeavesExactlyOneLeaderAndPreservesProjectRoles() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setRecruiting(false);
        em.flush();

        mvc.perform(transfer(memberProfile.getId()).session(session(leader)))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        assertThat(members()).satisfiesExactly(
                original -> {
                    assertThat(original.getMemberType()).isEqualTo(ProjectMemberType.MEMBER);
                    assertThat(original.getRole()).isEqualTo(ProjectRole.BACKEND);
                },
                promoted -> {
                    assertThat(promoted.getMemberType()).isEqualTo(ProjectMemberType.LEADER);
                    assertThat(promoted.getRole()).isEqualTo(ProjectRole.FRONTEND);
                });

        mvc.perform(transfer(memberProfile.getId()).session(session(leader)))
                .andExpect(status().isForbidden());
        mvc.perform(transfer(leaderProfile.getId()).session(session(member)))
                .andExpect(status().isNoContent());
        assertOriginalLeader();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onlyTheCurrentLeaderCanTransfer(boolean isMember) throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        em.flush();
        Account actor = isMember ? member : outsider;

        mvc.perform(transfer(memberProfile.getId()).session(session(actor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        assertOriginalLeader();
    }

    @Test
    void transferRejectsSelfAndMembersFromOtherProjects() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        var otherProject = newProject("Other project", ProjectStatus.IN_PROGRESS, false);
        var otherProfile = profile(outsider, true);
        persist(new ProjectMember(otherProject, otherProfile, ProjectMemberType.MEMBER, ProjectRole.AI));
        em.flush();

        for (Long target : List.of(leaderProfile.getId(), otherProfile.getId(), Long.MAX_VALUE)) {
            mvc.perform(transfer(target).session(session(leader)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors.targetProfileId").isString());
        }
        assertOriginalLeader();
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"PREPARING", "COMPLETED"})
    void transferRequiresAnInProgressProject(ProjectStatus state) throws Exception {
        project.setStatus(state);
        em.flush();
        mvc.perform(transfer(memberProfile.getId()).session(session(leader)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
        assertOriginalLeader();
    }

    @Test
    void requiresLoginAnExistingProjectAndAValidTargetId() throws Exception {
        mvc.perform(transfer(memberProfile.getId())).andExpect(status().isUnauthorized());
        mvc.perform(patch("/projects/{projectId}/leader", Long.MAX_VALUE)
                        .session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfileId\":" + memberProfile.getId() + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(transfer(0L).session(session(leader)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetProfileId").isString());
    }

    private MockHttpServletRequestBuilder transfer(Long targetProfileId) {
        return patch(path() + "/leader").contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetProfileId\":" + targetProfileId + "}");
    }

    private void assertOriginalLeader() {
        assertThat(members()).extracting(ProjectMember::getMemberType)
                .containsExactly(ProjectMemberType.LEADER, ProjectMemberType.MEMBER);
    }

    private List<ProjectMember> members() {
        return em.createQuery("""
                        select m from ProjectMember m where m.project.id = :projectId order by m.id
                        """, ProjectMember.class)
                .setParameter("projectId", project.getId())
                .getResultList();
    }
}
