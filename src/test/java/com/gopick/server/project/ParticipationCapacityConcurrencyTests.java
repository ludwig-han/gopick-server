package com.gopick.server.project;

import com.gopick.server.account.domain.Account;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.project.domain.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ParticipationCapacityConcurrencyTests extends ProjectApiFixture {
    @Autowired TransactionTemplate transactions;
    private Account secondCandidate;
    private Profile firstProfile, secondProfile;
    @Override @BeforeEach void setUp() {
        transactions.executeWithoutResult(tx -> {
            super.setUp();
            firstProfile = profile(outsider, true);
            secondCandidate = persist(new Account("second@example.com", "encoded-secret"));
            secondProfile = profile(secondCandidate, true);
        });
    }
    @AfterEach void cleanup() {
        var ids = List.of(leader.getId(), member.getId(), outsider.getId(), secondCandidate.getId());
        transactions.executeWithoutResult(tx -> {
            em.createQuery("delete from ProjectMember m where m.project.id = :id").setParameter("id", project.getId()).executeUpdate();
            em.remove(em.find(Project.class, project.getId()));
            em.flush();
            em.createQuery("delete from Profile p where p.account.id in :ids").setParameter("ids", ids).executeUpdate();
            em.createQuery("delete from Account a where a.id in :ids").setParameter("ids", ids).executeUpdate();
        });
    }
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void simultaneousAcceptancesSerializeAndNeverMakeCountNegative(int initialCount) throws Exception {
        transactions.executeWithoutResult(tx -> persist(new Recruitment(em.find(Project.class, project.getId()),
                ProjectRole.BACKEND, initialCount, ProjectConditionMode.NONE, List.of())));
        int first = offer(firstProfile.getId());
        int second = offer(secondProfile.getId());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> accept(outsider, first, ready, start));
            var b = pool.submit(() -> accept(secondCandidate, second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(200);
        }
        Integer remaining = transactions.execute(tx -> em.createQuery("select r.remainingCount from Recruitment r where r.project.id = :id and r.role = :role", Integer.class)
                .setParameter("id", project.getId()).setParameter("role", ProjectRole.BACKEND).getSingleResult());
        assertThat(remaining).isZero();
        Long memberCount = transactions.execute(tx -> em.createQuery("select count(m) from ProjectMember m where m.project.id = :id", Long.class)
                .setParameter("id", project.getId()).getSingleResult());
        assertThat(memberCount).isEqualTo(4);
    }
    private int offer(Long id) throws Exception {
        String response = mvc.perform(post(path() + "/participation-requests").session(session(leader))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OFFER\",\"role\":\"BACKEND\",\"targetProfileId\":" + id + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.requestId");
    }
    private int accept(Account user, int id, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Requests did not start together");
        return mvc.perform(patch(path() + "/participation-requests/" + id).session(session(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andReturn().getResponse().getStatus();
    }
}
