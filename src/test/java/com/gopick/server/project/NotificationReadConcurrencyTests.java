package com.gopick.server.project;

import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationReadConcurrencyTests extends ProjectApiFixture {
    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private NotificationService notifications;

    @Override
    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(transaction -> {
            super.setUp();
            notifications.send(leader, NotificationType.PROJECT_STARTED, project, null, null);
        });
    }

    @AfterEach
    void removeCommittedFixture() {
        var accountIds = List.of(leader.getId(), member.getId(), outsider.getId());
        transactions.executeWithoutResult(transaction -> {
            em.createQuery("delete from ProjectMember m where m.project.id = :id")
                    .setParameter("id", project.getId()).executeUpdate();
            em.createQuery("delete from Project p where p.id = :id")
                    .setParameter("id", project.getId()).executeUpdate();
            em.createQuery("delete from Profile p where p.account.id in :ids")
                    .setParameter("ids", accountIds).executeUpdate();
            em.createQuery("delete from Account a where a.id in :ids")
                    .setParameter("ids", accountIds).executeUpdate();
        });
    }

    @Test
    void concurrentReadRequestsKeepTheFirstPersistedTimestamp() throws Exception {
        Long recipientId = leader.getId();
        Long notificationId = notifications.list(recipientId).getFirst().id();
        String rawId = notificationId.toString();
        var firstWriteFlushed = new CountDownLatch(1);
        var allowFirstCommit = new CountDownLatch(1);
        var secondRequestStarted = new CountDownLatch(1);
        var secondRequestMarked = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(transaction -> {
                notifications.markRead(recipientId, rawId);
                em.flush();
                LocalDateTime firstReadAt = em.createQuery(
                                "select n.readAt from Notification n where n.id = :id", LocalDateTime.class)
                        .setParameter("id", notificationId).getSingleResult();
                firstWriteFlushed.countDown();
                waitFor(allowFirstCommit);
                return firstReadAt;
            }));

            try {
                waitFor(firstWriteFlushed);
                var second = executor.submit(() -> transactions.executeWithoutResult(transaction -> {
                    secondRequestStarted.countDown();
                    notifications.markRead(recipientId, rawId);
                    secondRequestMarked.countDown();
                }));
                waitFor(secondRequestStarted);
                assertThat(secondRequestMarked.await(500, TimeUnit.MILLISECONDS))
                        .as("the second reader must wait for the first transaction to commit")
                        .isFalse();

                allowFirstCommit.countDown();
                LocalDateTime firstReadAt = first.get(8, TimeUnit.SECONDS);
                second.get(8, TimeUnit.SECONDS);
                assertThat(firstReadAt).isNotNull();
                assertThat(notifications.list(recipientId).getFirst().readAt()).isEqualTo(firstReadAt);
            } finally {
                allowFirstCommit.countDown();
            }
        }
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            assertThat(latch.await(8, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
