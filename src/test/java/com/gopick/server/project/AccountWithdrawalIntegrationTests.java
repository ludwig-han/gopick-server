package com.gopick.server.project;

import com.gopick.server.project.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountWithdrawalIntegrationTests extends ProjectApiFixture {
    @Test
    void completedHistoryIsAnonymousAndCurrentSessionIsInvalidated() throws Exception {
        project.setStatus(ProjectStatus.COMPLETED);
        var login = session(member);
        var oldSession = session(member);
        Long profileId = memberProfile.getId();
        mvc.perform(delete("/me").session(login)).andExpect(status().isNoContent());
        em.flush(); em.clear();
        assertThat(em.find(com.gopick.server.profile.domain.Profile.class, profileId)).isNull();
        assertThat(em.find(com.gopick.server.account.domain.Account.class, member.getId())).isNull();
        mvc.perform(get(path()).session(session(leader))).andExpect(status().isOk())
                .andExpect(jsonPath("$.members[1].name").value("탈퇴한 회원입니다"))
                .andExpect(jsonPath("$.members[1].profileId").doesNotExist());
        mvc.perform(get("/me").session(oldSession)).andExpect(status().isUnauthorized());
        assertThat(login.isInvalid()).isTrue();
    }

    @Test
    void preparingLeaderDeletesProjectAndInProgressLeaderBlocksEntireWithdrawal() throws Exception {
        var active = newProject("active", ProjectStatus.IN_PROGRESS, false);
        persist(new ProjectMember(active, leaderProfile, ProjectMemberType.LEADER, ProjectRole.BACKEND));
        mvc.perform(delete("/me").session(session(leader))).andExpect(status().isConflict());
        assertThat(em.find(com.gopick.server.account.domain.Account.class, leader.getId())).isNotNull();
        assertThat(em.find(Project.class, project.getId())).isNotNull();
        active.setStatus(ProjectStatus.COMPLETED);
        mvc.perform(delete("/me").session(session(leader))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        assertThat(em.find(Project.class, project.getId())).isNull();
        mvc.perform(get("/projects/"+active.getId()).session(session(member))).andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].name").value("탈퇴한 회원입니다"));
    }

    @Test
    void sentAndReceivedRequestsAndBookmarksAreRemoved() throws Exception {
        var candidate = profile(outsider, true);
        mvc.perform(post(path()+"/participation-requests").session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"APPLY\",\"role\":\"BACKEND\"}")).andExpect(status().isCreated());
        mvc.perform(post("/profiles/"+candidate.getId()+"/bookmarks").session(session(leader))).andExpect(status().isNoContent());
        mvc.perform(delete("/me").session(session(outsider))).andExpect(status().isNoContent());
        mvc.perform(get("/me/participation-requests/received").session(session(leader))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/me/bookmarked-profiles").session(session(leader))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void softDeletedProjectsDoNotPreventWithdrawal() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.markDeleted();
        em.flush();
        mvc.perform(delete("/me").session(session(leader))).andExpect(status().isNoContent());
        em.flush();
        Number count = (Number) em.createNativeQuery("select count(*) from project_member where project_id = :project and profile_id is null")
                .setParameter("project", project.getId()).getSingleResult();
        assertThat(count.longValue()).isZero();
        assertThat(em.find(com.gopick.server.account.domain.Account.class, leader.getId())).isNull();
    }

    @Test
    void accountWithoutProfileCanWithdrawAndUnauthenticatedCannot() throws Exception {
        mvc.perform(delete("/me")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/me").session(session(outsider))).andExpect(status().isNoContent());
        em.flush();
        assertThat(em.find(com.gopick.server.account.domain.Account.class, outsider.getId())).isNull();
    }
}
