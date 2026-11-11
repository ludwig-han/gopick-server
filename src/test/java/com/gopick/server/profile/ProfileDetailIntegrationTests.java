package com.gopick.server.profile;

import com.gopick.server.account.domain.Account;
import com.gopick.server.profile.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileDetailIntegrationTests {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Test
    void signupSessionCanReadAnotherPublicProfileWithoutCreatingOwnProfile() throws Exception {
        Profile target = profile(account("owner"), true);
        var signup = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"viewer@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileId").isEmpty())
                .andReturn();
        MockHttpSession session = (MockHttpSession) signup.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/profiles/{profileId}", target.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.profileId").value(target.getId().intValue()))
                .andExpect(jsonPath("$.isPublic").value(true))
                .andExpect(jsonPath("$.contactPlatform").doesNotExist())
                .andExpect(jsonPath("$.contactId").doesNotExist())
                .andExpect(jsonPath("$.account").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void ownerCanReadOwnPrivateProfile() throws Exception {
        Account owner = account("owner");
        Profile target = profile(owner, false);

        mockMvc.perform(get("/profiles/{profileId}", target.getId()).session(session(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublic").value(false))
                .andExpect(jsonPath("$.contactId").value("owner-contact"))
                .andExpect(jsonPath("$.contactPlatform").value("DISCORD"));
    }

    @Test
    void anotherUserCannotReadPrivateProfileEvenWithDirectLink() throws Exception {
        Profile target = profile(account("owner"), false);
        Account viewer = account("viewer");

        mockMvc.perform(get("/profiles/{profileId}", target.getId()).session(session(viewer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVATE_PROFILE"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.contactId").doesNotExist())
                .andExpect(jsonPath("$.experiences").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void anonymousCannotReadEitherPublicOrPrivateProfile(boolean isPublic) throws Exception {
        Profile target = profile(account("owner"), isPublic);

        var result = mockMvc.perform(get("/profiles/{profileId}", target.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void anonymousWithInvalidIdIsUnauthenticated(String profileId) throws Exception {
        mockMvc.perform(get("/profiles/{profileId}", profileId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void sessionWithoutAccountIdIsUnauthenticated() throws Exception {
        Profile target = profile(account("owner"), true);

        mockMvc.perform(get("/profiles/{profileId}", target.getId()).session(new MockHttpSession()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void deletedAccountSessionIsUnauthenticated() throws Exception {
        Profile target = profile(account("owner"), true);
        MockHttpSession staleSession = new MockHttpSession();
        staleSession.setAttribute("accountId", Long.MAX_VALUE);

        mockMvc.perform(get("/profiles/{profileId}", target.getId()).session(staleSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/profiles/{profileId}", "abc").session(staleSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void missingProfileReturnsV2NotFoundError() throws Exception {
        mockMvc.perform(get("/profiles/{profileId}", Long.MAX_VALUE).session(session(account("viewer"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidIdReturnsCommonValidationError(String profileId) throws Exception {
        mockMvc.perform(get("/profiles/{profileId}", profileId).session(session(account("viewer"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.errors.profileId").isString());
    }

    @Test
    void profileWithoutRelatedDataReturnsEmptyCollections() throws Exception {
        Account owner = account("owner");
        Profile target = profile(owner, true);

        mockMvc.perform(get("/profiles/{profileId}", target.getId()).session(session(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts").isEmpty())
                .andExpect(jsonPath("$.technologies").isEmpty())
                .andExpect(jsonPath("$.interests").isEmpty())
                .andExpect(jsonPath("$.preferredRoles").isEmpty())
                .andExpect(jsonPath("$.preferences").isEmpty())
                .andExpect(jsonPath("$.experiences").isEmpty());
    }

    @Test
    void detailIncludesOnlyTargetProfileDataAndUsesV2FieldNames() throws Exception {
        Account owner = account("owner");
        Profile target = profile(owner, true);
        Account viewer = account("viewer");
        Profile other = profile(viewer, false);

        ProfileExperience first = addTargetProfileDetails(target);
        addUnrelatedProfileDetails(other);
        entityManager.flush();
        entityManager.clear();

        var result = mockMvc.perform(get("/profiles/{profileId}", target.getId()).session(session(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("owner"))
                .andExpect(jsonPath("$.major").value("컴퓨터공학"))
                .andExpect(jsonPath("$.contactPlatform").doesNotExist())
                .andExpect(jsonPath("$.contactId").doesNotExist())
                .andExpect(jsonPath("$.teamTendency").value("FOLLOW"))
                .andExpect(jsonPath("$.intro").value("owner-intro"))
                .andExpect(jsonPath("$.parts.length()").value(1))
                .andExpect(jsonPath("$.parts[0]").value("BACKEND"))
                .andExpect(jsonPath("$.technologies.length()").value(1))
                .andExpect(jsonPath("$.technologies[0].technology").value("Java"))
                .andExpect(jsonPath("$.technologies[0].level").value(3))
                .andExpect(jsonPath("$.interests[0]").value("WEB"))
                .andExpect(jsonPath("$.preferredRoles[0]").value("BACKEND"))
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.preferences[0].category").value("COLLAB"))
                .andExpect(jsonPath("$.preferences[0].value").value("정기 회의"))
                .andExpect(jsonPath("$.experiences.length()").value(2))
                .andExpect(jsonPath("$.experiences[0].experienceId").value(first.getId().intValue()))
                .andExpect(jsonPath("$.experiences[0].description").value("팀 프로젝트 구현"))
                .andExpect(jsonPath("$.experiences[0].attributes.length()").value(2))
                .andExpect(jsonPath("$.experiences[0].attributes[0].category").value("TYPE"))
                .andExpect(jsonPath("$.experiences[0].attributes[1].value").value("Java"))
                .andExpect(jsonPath("$.experiences[1].attributes").isEmpty())
                .andExpect(jsonPath("$.account").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("SHOULD_NOT_LEAK", "encoded-password", "@example.com");
    }

    private Account account(String name) {
        return persist(new Account(name + "@example.com", "encoded-password"));
    }

    private Profile profile(Account owner, boolean isPublic) {
        String name = owner.getEmail().split("@")[0];
        return persist(new Profile(owner, name, "컴퓨터공학", "DISCORD",
                name + "-contact", isPublic, "FOLLOW", name + "-intro"));
    }

    private ProfileExperience addTargetProfileDetails(Profile target) {
        persist(new ProfilePart(target, "BACKEND"));
        persist(new ProfileTechnology(target, "Java", 3));
        persist(new ProfileInterest(target, "WEB"));
        persist(new ProfileRole(target, "BACKEND"));
        persist(new ProfilePreference(target, ProfilePreferenceCategory.COLLAB, "정기 회의"));

        ProfileExperience first = persist(new ProfileExperience(target, "팀 프로젝트 구현"));
        persist(new ExperienceAttribute(first, ExperienceAttributeCategory.TYPE, "TEAM"));
        persist(new ExperienceAttribute(first, ExperienceAttributeCategory.TECHNOLOGY, "Java"));
        persist(new ProfileExperience(target, "학습 프로젝트"));
        return first;
    }

    private void addUnrelatedProfileDetails(Profile other) {
        String unrelatedValue = "SHOULD_NOT_LEAK";
        persist(new ProfilePart(other, unrelatedValue));
        persist(new ProfileTechnology(other, unrelatedValue, 4));
        persist(new ProfileInterest(other, unrelatedValue));
        persist(new ProfileRole(other, unrelatedValue));
        persist(new ProfilePreference(other, ProfilePreferenceCategory.COLLAB, unrelatedValue));

        ProfileExperience experience = persist(new ProfileExperience(other, unrelatedValue));
        persist(new ExperienceAttribute(experience, ExperienceAttributeCategory.TYPE, unrelatedValue));
    }

    private MockHttpSession session(Account account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("accountId", account.getId());
        return session;
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
