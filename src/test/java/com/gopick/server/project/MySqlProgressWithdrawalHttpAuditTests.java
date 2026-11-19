package com.gopick.server.project;

import com.gopick.server.account.domain.Account;
import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfilePart;
import com.gopick.server.profile.domain.ProfileInterest;
import com.gopick.server.profile.domain.ProfileRole;
import com.gopick.server.profile.domain.ProfileTechnology;
import com.gopick.server.project.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

// Only run with an explicitly configured, disposable MySQL database.
@EnabledIfEnvironmentVariable(named = "GOPICK_MYSQL_DEADLOCK_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MySqlProgressWithdrawalHttpAuditTests {
    private static final String PASSWORD = "QaConcurrency123a";
    @LocalServerPort private int port;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwords;
    @MockitoSpyBean private NotificationService notifications;

    @ParameterizedTest
    @EnumSource(Action.class)
    void aProgressVictimIsRetriedWithoutANewHttpRequest(Action action) throws Exception {
        Fixture fixture = transactions.execute(status -> seed(action));
        var progressReady = new CountDownLatch(1);
        var releaseProgress = new CountDownLatch(1);
        var progressConnection = new AtomicLong();
        var withdrawalConnection = new AtomicLong();
        var sqlFailure = new AtomicReference<SQLException>();
        var attempts = new AtomicInteger();

        doAnswer(invocation -> {
            attempts.incrementAndGet();
            progressReady.countDown();
            if (!releaseProgress.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Audit barrier timed out");
            }
            try {
                return invocation.callRealMethod();
            } catch (Throwable error) {
                for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                    if (cause instanceof SQLException sql) {
                        sqlFailure.set(sql);
                        break;
                    }
                }
                throw error;
            }
        }).when(notifications).sendToOtherMembers(
                argThat(project -> project != null && project.getId().equals(fixture.projectId())),
                eq(fixture.leaderId()), eq(action.type));

        try (HttpClient leaderClient = authenticated(fixture.leaderEmail());
             HttpClient memberClient = authenticated(fixture.withdrawingEmail());
             var executor = Executors.newFixedThreadPool(2)) {
            var progress = executor.submit(() -> requestProgress(leaderClient, fixture.projectId(), action));
            try {
                assertThat(progressReady.await(15, TimeUnit.SECONDS)).isTrue();
                var withdrawal = executor.submit(() -> request(memberClient, "DELETE", "/me", null));
                var lockWait = awaitProjectWait();
                progressConnection.set(((Number) lockWait.get("blocking_id")).longValue());
                withdrawalConnection.set(((Number) lockWait.get("requesting_id")).longValue());

                var weights = jdbc.queryForList("""
                        select trx_mysql_thread_id as connection_id, trx_weight, trx_lock_structs, trx_state
                        from information_schema.innodb_trx where trx_mysql_thread_id in (?, ?)
                        """, progressConnection.get(), withdrawalConnection.get());
                long progressWeight = weight(weights, progressConnection.get());
                long withdrawalWeight = weight(weights, withdrawalConnection.get());
                System.out.printf("OVERLAP_WEIGHTS action=%s progress=%d withdrawal=%d rows=%s%n",
                        action, progressWeight, withdrawalWeight, weights);
                releaseProgress.countDown();

                int progressStatus = progress.get(25, TimeUnit.SECONDS).statusCode();
                int withdrawalStatus = withdrawal.get(25, TimeUnit.SECONDS).statusCode();
                SQLException sql = sqlFailure.get();
                System.out.printf("OVERLAP_HTTP action=%s progress=%d withdrawal=%d sql=%s state=%s%n",
                        action, progressStatus, withdrawalStatus,
                        sql == null ? "none" : sql.getErrorCode(), sql == null ? "none" : sql.getSQLState());

                assertThat(progressStatus).isEqualTo(200);
                assertThat(withdrawalStatus).isEqualTo(204);
                assertThat((Throwable) sql).isNotNull();
                assertThat(sql.getErrorCode()).isEqualTo(1213);
                assertThat(sql.getSQLState()).isEqualTo("40001");
                assertThat(attempts.get()).isEqualTo(2);
                assertThat(jdbc.queryForObject("select count(*) from account where id = ?", Long.class,
                        fixture.withdrawingId())).isZero();
                assertThat(request(memberClient, "GET", "/me", null).statusCode()).isEqualTo(401);
                assertThat(notificationsForSurvivor(fixture, action)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select status from project where id = ?", String.class,
                        fixture.projectId())).isEqualTo(action.finalStatus.name());
                if (action == Action.STAGE) {
                    assertThat(jdbc.queryForObject("select stage from project where id = ?", String.class,
                            fixture.projectId())).isEqualTo("DESIGN");
                }
                System.out.printf("OVERLAP_FINAL action=%s attempts=%d progressHttp=%d survivorNotifications=%d%n",
                        action, attempts.get(), progressStatus, notificationsForSurvivor(fixture, action));
            } finally {
                releaseProgress.countDown();
            }
        }
    }

    private Fixture seed(Action action) {
        String suffix = UUID.randomUUID().toString();
        String encoded = passwords.encode(PASSWORD);
        var leader = persist(new Account("leader-" + suffix + "@example.com", encoded));
        var member = persist(new Account("member-" + suffix + "@example.com", encoded));
        var survivor = persist(new Account("survivor-" + suffix + "@example.com", encoded));
        var historyLeader = persist(new Account("history-" + suffix + "@example.com", encoded));
        var leaderProfile = profile(leader);
        var memberProfile = profile(member);
        var survivorProfile = profile(survivor);
        var historyLeaderProfile = profile(historyLeader);
        String padding = "테".repeat(255);
        // The projects are valid, committed application data spread over many InnoDB pages.
        for (int n = 0; n < 200; n++) {
            var history = persist(new Project(padding, padding, padding, padding, padding, padding, false));
            history.setStatus(ProjectStatus.COMPLETED);
            persist(new ProjectMember(history, historyLeaderProfile, ProjectMemberType.LEADER, ProjectRole.PM));
            persist(new ProjectMember(history, memberProfile, ProjectMemberType.MEMBER, ProjectRole.BACKEND));
        }
        var target = new Project("extra", "wish", "goal", "background", "description", "Concurrency QA", action == Action.START);
        target.setStatus(action.initialStatus);
        if (action != Action.START) {
            target.setStage(ProjectStage.PLANNING);
        }
        persist(target);
        persist(new ProjectMember(target, leaderProfile, ProjectMemberType.LEADER, ProjectRole.PM));
        persist(new ProjectMember(target, memberProfile, ProjectMemberType.MEMBER, ProjectRole.BACKEND));
        persist(new ProjectMember(target, survivorProfile, ProjectMemberType.MEMBER, ProjectRole.FRONTEND));
        em.flush();
        return new Fixture(target.getId(), leader.getId(), leader.getEmail(),
                member.getId(), member.getEmail(), survivor.getId());
    }

    private Profile profile(Account account) {
        var profile = persist(new Profile(account, "QA", "CS", "DISCORD", "qa", true, "FOLLOW", "audit"));
        persist(new ProfilePart(profile, "Dev"));
        persist(new ProfileInterest(profile, "웹"));
        persist(new ProfileRole(profile, "BACKEND"));
        persist(new ProfileTechnology(profile, "Java", 3));
        return profile;
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private HttpClient authenticated(String email) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        assertThat(request(client, "POST", "/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}").statusCode()).isEqualTo(200);
        return client;
    }

    private HttpResponse<String> requestProgress(HttpClient client, Long projectId, Action action) throws Exception {
        return request(client, action == Action.STAGE ? "PATCH" : "POST",
                "/projects/" + projectId + "/" + action.path, action == Action.STAGE ? "{\"stage\":\"DESIGN\"}" : null);
    }

    private HttpResponse<String> request(HttpClient client, String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> awaitProjectWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            var waits = jdbc.queryForList("""
                    select requested.processlist_id as requesting_id, blocked.processlist_id as blocking_id
                    from performance_schema.data_lock_waits w
                    join performance_schema.threads requested on requested.thread_id = w.requesting_thread_id
                    join performance_schema.threads blocked on blocked.thread_id = w.blocking_thread_id
                    """);
            if (!waits.isEmpty()) {
                return waits.getFirst();
            }
            Thread.sleep(25);
        }
        System.out.println("OVERLAP_OBSERVER transactions=" + jdbc.queryForList(
                "select trx_mysql_thread_id, trx_state, trx_weight, trx_query from information_schema.innodb_trx"));
        throw new IllegalStateException("Withdrawal did not wait for the held project lock");
    }
    private long weight(java.util.List<Map<String, Object>> transactions, long connectionId) {
        return transactions.stream()
                .filter(row -> ((Number) row.get("connection_id")).longValue() == connectionId)
                .mapToLong(row -> ((Number) row.get("trx_weight")).longValue())
                .findFirst().orElse(-1);
    }

    private long notificationsForSurvivor(Fixture fixture, Action action) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_id = ? and project_id = ? and type = ?",
                Long.class, fixture.survivorId(), fixture.projectId(), action.type.name());
    }

    private record Fixture(Long projectId, Long leaderId, String leaderEmail,
                           Long withdrawingId, String withdrawingEmail, Long survivorId) {}

    private enum Action {
        START("start", NotificationType.PROJECT_STARTED, ProjectStatus.PREPARING, ProjectStatus.IN_PROGRESS),
        COMPLETE("complete", NotificationType.PROJECT_COMPLETED, ProjectStatus.IN_PROGRESS, ProjectStatus.COMPLETED),
        STAGE("stage", NotificationType.PROJECT_STAGE_CHANGED, ProjectStatus.IN_PROGRESS, ProjectStatus.IN_PROGRESS);

        private final String path;
        private final NotificationType type;
        private final ProjectStatus initialStatus;
        private final ProjectStatus finalStatus;

        Action(String path, NotificationType type, ProjectStatus initialStatus, ProjectStatus finalStatus) {
            this.path = path;
            this.type = type;
            this.initialStatus = initialStatus;
            this.finalStatus = finalStatus;
        }
    }
}
