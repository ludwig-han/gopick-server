package com.gopick.server.project;

import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.ProjectStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectLeaderTransferConcurrencyTests extends ProjectApiFixture {
    @Autowired
    TransactionTemplate transactions;

    private Long secondTargetId;

    @Override
    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(transaction -> {
            super.setUp();
            var secondTarget = profile(outsider, true);
            secondTargetId = secondTarget.getId();
            persist(new ProjectMember(project, secondTarget, ProjectMemberType.MEMBER, ProjectRole.AI));
            project.setStatus(ProjectStatus.IN_PROGRESS);
            project.setRecruiting(false);
        });
    }

    @Test
    void twoTransfersFromTheSameLeaderCannotPromoteTwoMembers() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> transfer(memberProfile.getId(), ready, start));
            var second = executor.submit(() -> transfer(secondTargetId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);
            assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(204, 403);
        }
        Long leaderCount = transactions.execute(transaction -> em.createQuery("""
                        select count(m) from ProjectMember m
                        where m.project.id = :projectId and m.memberType = :leader
                        """, Long.class)
                .setParameter("projectId", project.getId())
                .setParameter("leader", ProjectMemberType.LEADER)
                .getSingleResult());
        assertThat(leaderCount).isEqualTo(1);
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

    private int transfer(Long target, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent request did not start");
        }
        return mvc.perform(patch(path() + "/leader").session(session(leader))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetProfileId\":" + target + "}"))
                .andReturn().getResponse().getStatus();
    }
}
