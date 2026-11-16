package com.gopick.server.project;

import com.gopick.server.project.service.ProjectAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:project-progress-notifications;MODE=MySQL;NON_KEYWORDS=VALUE",
        // H2 SNAPSHOT models MySQL REPEATABLE READ across both account and membership queries.
        "spring.datasource.hikari.connection-init-sql=SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL SNAPSHOT"
})
@AutoConfigureMockMvc
@DirtiesContext
class ProjectProgressNotificationConcurrencyTests extends ProjectApiFixture {
    @Autowired
    private TransactionTemplate transactions;

    @MockitoSpyBean
    private ProjectAccess projectAccess;

    @Override
    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(transaction -> super.setUp());
    }

    @Test
    void startExcludesMembersWhoWithdrawAfterTheAccountCheck() throws Exception {
        var accountChecked = new CountDownLatch(1);
        var memberWithdrew = new CountDownLatch(1);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            accountChecked.countDown();
            if (!memberWithdrew.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The withdrawal did not finish");
            }
            return null;
        }).when(projectAccess).requireAccount(leader.getId());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var start = executor.submit(() ->
                    mvc.perform(post(path() + "/start").session(session(leader)))
                            .andExpect(status().isOk()));

            try {
                assertThat(accountChecked.await(5, TimeUnit.SECONDS)).isTrue();
                mvc.perform(delete(path() + "/members/me").session(session(member)))
                        .andExpect(status().isNoContent());
            } finally {
                memberWithdrew.countDown();
            }

            start.get(10, TimeUnit.SECONDS);
        }

        mvc.perform(get("/me/notifications").session(session(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
