package com.gopick.server.project;

import com.gopick.server.account.domain.Account;
import com.gopick.server.project.domain.ParticipationRequest;
import com.gopick.server.project.domain.ParticipationRequestType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class ParticipationRequestConcurrencyTests extends ProjectApiFixture {
    @Autowired
    TransactionTemplate transactions;

    private Long candidateId;

    @Override
    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(transaction -> {
            super.setUp();
            candidateId = profile(outsider, true).getId();
        });
    }

    @Test
    void simultaneousCreateAndAcceptProduceOneRequestAndOneMembership() throws Exception {
        assertThat(race(
                () -> create(outsider, "{\"type\":\"APPLY\",\"role\":\"BACKEND\"}"),
                () -> create(leader, "{\"type\":\"OFFER\",\"role\":\"DESIGN\",\"targetProfileId\":" + candidateId + "}")
        )).containsExactlyInAnyOrder(201, 409);

        var request = transactions.execute(transaction -> em.createQuery("""
                        select r from ParticipationRequest r where r.project.id = :projectId
                        """, ParticipationRequest.class)
                .setParameter("projectId", project.getId()).getSingleResult());
        Account approver = request.getType() == ParticipationRequestType.APPLY ? leader : outsider;

        assertThat(race(
                () -> accept(approver, request.getId()),
                () -> accept(approver, request.getId())
        )).containsExactlyInAnyOrder(200, 409);

        Long count = transactions.execute(transaction -> em.createQuery("""
                        select count(m) from ProjectMember m
                        where m.project.id = :projectId and m.profile.id = :candidateId
                        """, Long.class)
                .setParameter("projectId", project.getId())
                .setParameter("candidateId", candidateId).getSingleResult());
        assertThat(count).isEqualTo(1L);
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

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var firstResult = executor.submit(() -> afterStart(first, ready, start));
            var secondResult = executor.submit(() -> afterStart(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        }
    }

    private int afterStart(Callable<Integer> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent request did not start");
        }
        return action.call();
    }

    private int create(Account actor, String body) throws Exception {
        return mvc.perform(post(path() + "/participation-requests").session(session(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private int accept(Account actor, long id) throws Exception {
        return mvc.perform(patch(path() + "/participation-requests/" + id).session(session(actor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andReturn().getResponse().getStatus();
    }
}
