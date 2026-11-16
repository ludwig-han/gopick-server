package com.gopick.server.project;

import com.gopick.server.account.domain.Account;
import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import com.gopick.server.project.domain.Project;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:project-exit-retry;MODE=MySQL;NON_KEYWORDS=VALUE")
@AutoConfigureMockMvc
class ProjectExitNotificationDeadlockRetryTests extends ProjectApiFixture {
    @Autowired
    private TransactionTemplate transactions;

    @MockitoSpyBean
    private NotificationService notifications;

    @Override
    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(transaction -> super.setUp());
    }

    @ParameterizedTest
    @EnumSource(ExitAction.class)
    void retriesTheWholeExitAndCommitsOneNotification(ExitAction action) throws Exception {
        boolean memberLeaves = action == ExitAction.MEMBER_LEAVES;
        Account actor = memberLeaves ? member : leader;
        Account recipient = memberLeaves ? leader : member;
        var actorSession = session(actor);
        var expectedType = memberLeaves ? NotificationType.MEMBER_LEFT : NotificationType.PROJECT_DELETED;
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (attempts.incrementAndGet() == 1) {
                throw new CannotAcquireLockException("Simulated deadlock after inserting the notification",
                        new SQLException("Deadlock found when trying to get lock", "40001", 1213));
            }
            return null;
        }).when(notifications).send(argThat(account -> account != null && recipient.getId().equals(account.getId())),
                eq(expectedType), any(Project.class), isNull(), eq(actor.getId()));

        String endpoint = switch (action) {
            case MEMBER_LEAVES, LEADER_LEAVES -> path() + "/members/me";
            case LEADER_DELETES -> path();
            case LEADER_WITHDRAWS_ACCOUNT -> "/me";
        };
        mvc.perform(delete(endpoint).session(actorSession)).andExpect(status().isNoContent());

        assertThat(attempts.get()).isEqualTo(2);
        mvc.perform(get("/me/notifications").session(session(recipient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value(expectedType.name()))
                .andExpect(jsonPath("$[0].projectId").value(project.getId().intValue()));
        if (memberLeaves) {
            mvc.perform(get(path()).session(session(recipient)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.members.length()").value(1))
                    .andExpect(jsonPath("$.members[0].profileId").value(leaderProfile.getId().intValue()));
        } else {
            mvc.perform(get(path()).session(session(recipient))).andExpect(status().isNotFound());
        }
        if (action == ExitAction.LEADER_WITHDRAWS_ACCOUNT) {
            assertThat(actorSession.isInvalid()).isTrue();
            mvc.perform(get("/me").session(session(actor))).andExpect(status().isUnauthorized());
        }
    }

    @AfterEach
    void removeCommittedFixture() {
        var ids = List.of(leader.getId(), member.getId(), outsider.getId());
        transactions.executeWithoutResult(transaction -> {
            em.createNativeQuery("delete from project_member where project_id = :id")
                    .setParameter("id", project.getId()).executeUpdate();
            em.createNativeQuery("delete from project where id = :id")
                    .setParameter("id", project.getId()).executeUpdate();
            em.createQuery("delete from Profile p where p.account.id in :ids")
                    .setParameter("ids", ids).executeUpdate();
            em.createQuery("delete from Account a where a.id in :ids")
                    .setParameter("ids", ids).executeUpdate();
        });
    }

    enum ExitAction {
        MEMBER_LEAVES, LEADER_LEAVES, LEADER_DELETES, LEADER_WITHDRAWS_ACCOUNT
    }
}
