package com.gopick.server.project;

import com.gopick.server.project.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountWithdrawalIntegrationTests extends ProjectApiFixture {
    @Autowired
    private NotificationService notifications;

    @Test
    void withdrawalDeletesRecipientsNotificationsButPreservesOtherUsersNotifications() throws Exception {
        mvc.perform(post(path() + "/start").session(session(leader))).andExpect(status().isOk());
        mvc.perform(patch(path() + "/leader").session(session(leader))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetProfileId\":" + memberProfile.getId() + "}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/me/notifications").session(session(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        // The former leader is now a regular member and can withdraw.
        mvc.perform(post(path() + "/complete").session(session(member))).andExpect(status().isOk());
        mvc.perform(get("/me/notifications").session(session(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        Long removedAccountId = leader.getId();
        var staleSession = session(leader);
        mvc.perform(delete("/me").session(session(leader))).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        Number remaining = (Number) em.createNativeQuery("select count(*) from notification where recipient_id = :id")
                .setParameter("id", removedAccountId).getSingleResult();
        assertThat(remaining.longValue()).isZero();
        mvc.perform(get("/me/notifications").session(staleSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/notifications").session(session(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void notificationFanoutSkipsAnonymizedCompletedProjectMember() throws Exception {
        var otherProfile = profile(outsider, true);
        persist(new ProjectMember(project, otherProfile, ProjectMemberType.MEMBER, ProjectRole.AI));
        project.setStatus(ProjectStatus.COMPLETED);
        mvc.perform(delete("/me").session(session(member))).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        var storedProject = em.find(Project.class, project.getId());
        notifications.sendToOtherMembers(storedProject, leader.getId(), NotificationType.PROJECT_COMPLETED);
        em.flush();
        mvc.perform(get("/me/notifications").session(session(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PROJECT_COMPLETED"));
        mvc.perform(get("/me/notifications").session(session(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

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
