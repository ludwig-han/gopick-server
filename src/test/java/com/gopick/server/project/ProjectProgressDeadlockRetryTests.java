package com.gopick.server.project;

import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import com.gopick.server.project.domain.ProjectStage;
import com.gopick.server.project.domain.ProjectStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:progress-retry;MODE=MySQL;NON_KEYWORDS=VALUE")
@AutoConfigureMockMvc
class ProjectProgressDeadlockRetryTests extends ProjectApiFixture {
    @Autowired private TransactionTemplate transactions;
    @MockitoSpyBean private NotificationService notifications;

    @Override @BeforeEach void setUp() {
        transactions.executeWithoutResult(tx -> super.setUp());
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void retriesRolledBackProgressAndDeliversOneNotification(Action action) throws Exception {
        transactions.executeWithoutResult(tx -> {
            var current = em.find(com.gopick.server.project.domain.Project.class, project.getId());
            if (action != Action.START) {
                current.setStatus(ProjectStatus.IN_PROGRESS);
                current.setStage(ProjectStage.PLANNING);
            }
        });
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (attempts.incrementAndGet() == 1) {
                throw new CannotAcquireLockException("Simulated deadlock after notification insert",
                        new SQLException("deadlock", "40001", 1213));
            }
            return null;
        }).when(notifications).sendToOtherMembers(
                argThat(p -> p != null && p.getId().equals(project.getId())), eq(leader.getId()), eq(action.type));

        var endpoint = path() + "/" + action.path;
        var request = action == Action.STAGE
                ? patch(endpoint).contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"DESIGN\"}")
                : post(endpoint);
        mvc.perform(request.session(session(leader))).andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value(action.status.name()));
        assertThat(attempts.get()).isEqualTo(2);
        mvc.perform(get("/me/notifications").session(session(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value(action.type.name()));
        if (action == Action.STAGE) {
            ProjectStage stage = transactions.execute(tx -> em.find(com.gopick.server.project.domain.Project.class,
                    project.getId()).getStage());
            assertThat(stage).isEqualTo(ProjectStage.DESIGN);
        }
    }

    @AfterEach void cleanup() {
        transactions.executeWithoutResult(tx -> {
            em.createNativeQuery("delete from notification where project_id = :id").setParameter("id", project.getId()).executeUpdate();
            em.createNativeQuery("delete from project_member where project_id = :id").setParameter("id", project.getId()).executeUpdate();
            em.createNativeQuery("delete from project where id = :id").setParameter("id", project.getId()).executeUpdate();
            var ids = List.of(leader.getId(), member.getId(), outsider.getId());
            em.createQuery("delete from Profile p where p.account.id in :ids").setParameter("ids", ids).executeUpdate();
            em.createQuery("delete from Account a where a.id in :ids").setParameter("ids", ids).executeUpdate();
        });
    }

    enum Action {
        START("start", NotificationType.PROJECT_STARTED, ProjectStatus.IN_PROGRESS),
        COMPLETE("complete", NotificationType.PROJECT_COMPLETED, ProjectStatus.COMPLETED),
        STAGE("stage", NotificationType.PROJECT_STAGE_CHANGED, ProjectStatus.IN_PROGRESS);
        final String path;
        final NotificationType type;
        final ProjectStatus status;
        Action(String path, NotificationType type, ProjectStatus status) {
            this.path = path; this.type = type; this.status = status;
        }
    }
}
