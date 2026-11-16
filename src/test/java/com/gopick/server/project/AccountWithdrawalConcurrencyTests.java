package com.gopick.server.project;

import com.gopick.server.account.domain.Account;
import com.gopick.server.account.service.AccountWithdrawalService;
import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.service.ParticipantAccountLock;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountWithdrawalConcurrencyTests extends ProjectApiFixture {
    @Autowired TransactionTemplate transactions;
    @Autowired AccountWithdrawalService withdrawal;
    @Autowired NotificationService notifications;
    @MockitoSpyBean ParticipantAccountLock accountLock;

    @Override
    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(tx -> {
            super.setUp();
            project.setStatus(ProjectStatus.IN_PROGRESS);
        });
    }

    @AfterEach
    void cleanup() {
        var ids = List.of(leader.getId(), member.getId(), outsider.getId());
        transactions.executeWithoutResult(tx -> {
            em.createQuery("delete from ProjectMember m where m.project.id = :id").setParameter("id", project.getId()).executeUpdate();
            em.createQuery("delete from Project p where p.id = :id").setParameter("id", project.getId()).executeUpdate();
            em.createQuery("delete from Profile p where p.account.id in :ids").setParameter("ids", ids).executeUpdate();
            em.createQuery("delete from Account a where a.id in :ids").setParameter("ids", ids).executeUpdate();
        });
    }

    @Test
    void withdrawalHoldingAccountLetsTransferFinishWithoutDeadlock() throws Exception {
        var held = new CountDownLatch(1);
        var attempted = new CountDownLatch(1);
        doAnswer(invocation -> { attempted.countDown(); return invocation.callRealMethod(); })
                .when(accountLock).lockIfPresent(member.getId());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var withdrawing = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                em.find(Account.class, member.getId(), LockModeType.PESSIMISTIC_WRITE);
                held.countDown();
                await(attempted);
                withdrawal.withdraw(member.getId());
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            var transferring = pool.submit(() -> mvc.perform(patch(path() + "/leader").session(session(leader))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"targetProfileId\":" + memberProfile.getId() + "}"))
                    .andReturn().getResponse().getStatus());
            withdrawing.get(15, TimeUnit.SECONDS);
            assertThat(transferring.get(15, TimeUnit.SECONDS)).isEqualTo(400);
            assertLeaderIs(leader.getId());
            assertThat(notifications.list(leader.getId())).isEmpty();
            Long count = transactions.execute(tx -> em.createQuery(
                    "select count(n) from Notification n where n.recipient.id = :id", Long.class)
                    .setParameter("id", member.getId()).getSingleResult());
            assertThat(count).isZero();
        }
    }

    @Test
    void transferHoldingAccountMakesWithdrawalWaitAndThenRejectsNewLeader() throws Exception {
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            held.countDown();
            await(release);
            return result;
        }).when(accountLock).lockIfPresent(member.getId());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var transferring = pool.submit(() -> mvc.perform(patch(path() + "/leader").session(session(leader))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"targetProfileId\":" + memberProfile.getId() + "}"))
                    .andReturn().getResponse().getStatus());
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                var withdrawing = pool.submit(() -> mvc.perform(delete("/me").session(session(member)))
                        .andReturn().getResponse().getStatus());
                assertThatThrownBy(() -> withdrawing.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(transferring.get(15, TimeUnit.SECONDS)).isEqualTo(204);
                assertThat(withdrawing.get(15, TimeUnit.SECONDS)).isEqualTo(409);
                assertLeaderIs(member.getId());
                assertThat(notifications.list(member.getId())).singleElement()
                        .extracting(notification -> notification.type()).isEqualTo(NotificationType.LEADER_TRANSFERRED);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void acceptanceAndWithdrawalSerializeOnParticipantAccount() throws Exception {
        transactions.executeWithoutResult(tx -> {
            em.find(com.gopick.server.project.domain.Project.class, project.getId()).setStatus(ProjectStatus.PREPARING);
            profile(outsider, true);
        });
        String body = mvc.perform(post(path() + "/participation-requests").session(session(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"APPLY\",\"role\":\"BACKEND\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int requestId = com.jayway.jsonpath.JsonPath.read(body, "$.requestId");
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            held.countDown();
            await(release);
            return result;
        }).when(accountLock).lockIfPresent(outsider.getId());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var accepting = pool.submit(() -> mvc.perform(patch(path() + "/participation-requests/" + requestId)
                    .session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"ACCEPTED\"}")).andReturn().getResponse().getStatus());
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                var withdrawing = pool.submit(() -> mvc.perform(delete("/me").session(session(outsider)))
                        .andReturn().getResponse().getStatus());
                assertThatThrownBy(() -> withdrawing.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(accepting.get(15, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(withdrawing.get(15, TimeUnit.SECONDS)).isEqualTo(204);
                assertLeaderIs(leader.getId());
                assertThat(notifications.list(leader.getId())).singleElement().satisfies(notification -> {
                    assertThat(notification.type()).isEqualTo(NotificationType.APPLY_RECEIVED);
                    assertThat(notification.requestAvailable()).isFalse();
                });
                Long count = transactions.execute(tx -> em.createQuery(
                        "select count(n) from Notification n where n.recipient.id = :id", Long.class)
                        .setParameter("id", outsider.getId()).getSingleResult());
                assertThat(count).isZero();
            } finally { release.countDown(); }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLY", "OFFER", "ACCEPT"})
    void withdrawalBeforeRequestChangeLeavesNoDanglingRequest(String operation) throws Exception {
        Long candidateId = transactions.execute(tx -> {
            em.find(com.gopick.server.project.domain.Project.class, project.getId()).setStatus(ProjectStatus.PREPARING);
            return profile(outsider, true).getId();
        });
        int requestId;
        if (operation.equals("ACCEPT")) {
            String response = mvc.perform(post(path() + "/participation-requests").session(session(outsider))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"APPLY\",\"role\":\"BACKEND\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            requestId = com.jayway.jsonpath.JsonPath.read(response, "$.requestId");
        } else {
            requestId = 0;
        }
        var held = new CountDownLatch(1);
        var attempted = new CountDownLatch(1);
        doAnswer(invocation -> { attempted.countDown(); return invocation.callRealMethod(); })
                .when(accountLock).lockIfPresent(outsider.getId());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var withdrawing = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                em.find(Account.class, outsider.getId(), LockModeType.PESSIMISTIC_WRITE);
                held.countDown();
                await(attempted);
                withdrawal.withdraw(outsider.getId());
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            var other = pool.submit(() -> {
                var request = switch (operation) {
                    case "ACCEPT" -> patch(path() + "/participation-requests/" + requestId)
                            .content("{\"status\":\"ACCEPTED\"}");
                    case "APPLY" -> post(path() + "/participation-requests")
                            .content("{\"type\":\"APPLY\",\"role\":\"BACKEND\"}");
                    default -> post(path() + "/participation-requests")
                            .content("{\"type\":\"OFFER\",\"role\":\"BACKEND\",\"targetProfileId\":" + candidateId + "}");
                };
                return mvc.perform(request.session(session(operation.equals("APPLY") ? outsider : leader))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andReturn().getResponse().getStatus();
            });
            withdrawing.get(15, TimeUnit.SECONDS);
            assertThat(other.get(15, TimeUnit.SECONDS)).isEqualTo(operation.equals("APPLY") ? 401 : 404);
            assertLeaderIs(leader.getId());
        }
    }

    private void assertLeaderIs(Long expectedAccountId) {
        List<Long> leaders = transactions.execute(tx -> em.createQuery("""
                select m.profile.account.id from ProjectMember m
                where m.project.id = :id and m.memberType = com.gopick.server.project.domain.ProjectMemberType.LEADER
                """, Long.class).setParameter("id", project.getId()).getResultList());
        assertThat(leaders).containsExactly(expectedAccountId);
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for concurrent request"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
