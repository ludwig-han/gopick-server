package com.gopick.server.profile;

import com.gopick.server.account.domain.Account;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileInterest;
import com.gopick.server.profile.domain.ProfileRole;
import com.gopick.server.profile.domain.ProfileTechnology;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileSearchIntegrationTests {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Test
    void listsOnlyPublicProfilesAndOmitsContactInformation() throws Exception {
        Profile hidden = profile("hidden", false, "Java", "AI", "Backend");
        Profile visible = profile("visible", true, "Java", "AI", "Backend");

        mockMvc.perform(get("/profiles").session(session(hidden.getAccount().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].profileId", contains(visible.getId().intValue())))
                .andExpect(jsonPath("$[0].technologies[0].technology").value("Java"))
                .andExpect(jsonPath("$[0].interests[0]").value("AI"))
                .andExpect(jsonPath("$[0].preferredRoles[0]").value("Backend"))
                .andExpect(jsonPath("$[0].contactPlatform").doesNotExist())
                .andExpect(jsonPath("$[0].contactId").doesNotExist())
                .andExpect(jsonPath("$[0].account").doesNotExist());
    }

    @Test
    void appliesEverySuppliedFilterAndDoesNotDuplicateMatches() throws Exception {
        Profile match = profile("match", true, "Java", "AI", "Backend");
        profile("differentTech", true, "Python", "AI", "Backend");
        profile("differentInterest", true, "Java", "WEB", "Backend");
        profile("differentRole", true, "Java", "AI", "Frontend");
        profile("privateMatch", false, "Java", "AI", "Backend");
        entityManager.persist(new ProfileTechnology(match, "Java", 3));

        mockMvc.perform(get("/profiles")
                        .param("technology", " java ")
                        .param("interest", "ai")
                        .param("preferredRole", "BACKEND")
                        .session(session(match.getAccount().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].profileId", contains(match.getId().intValue())));

        mockMvc.perform(get("/profiles").param("technology", "Rust")
                        .session(session(match.getAccount().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void requiresAnActiveAccount() throws Exception {
        mockMvc.perform(get("/profiles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/profiles").session(session(Long.MAX_VALUE)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private Profile profile(String name, boolean isPublic, String technology, String interest, String role) {
        Account account = new Account(name + "@example.com", "test-hash");
        entityManager.persist(account);
        Profile profile = new Profile(account, name, "Computer Science", "DISCORD", "test-contact",
                isPublic, null, null);
        entityManager.persist(profile);
        entityManager.persist(new ProfileTechnology(profile, technology, 3));
        entityManager.persist(new ProfileInterest(profile, interest));
        entityManager.persist(new ProfileRole(profile, role));
        return profile;
    }

    private MockHttpSession session(Long accountId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("accountId", accountId);
        return session;
    }
}
