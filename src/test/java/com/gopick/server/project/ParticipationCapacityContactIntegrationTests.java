package com.gopick.server.project;

import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileContact;
import com.gopick.server.project.domain.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParticipationCapacityContactIntegrationTests extends ProjectApiFixture {
    private Profile candidate;
    @BeforeEach void candidate() { candidate = profile(outsider, false); }

    @ParameterizedTest
    @CsvSource({"APPLY,2,1", "OFFER,2,1", "APPLY,0,0", "OFFER,0,0"})
    void acceptingReducesMatchingRoleWithoutGoingBelowZero(String type, int before, int after) throws Exception {
        var recruitment = persist(new Recruitment(project, ProjectRole.BACKEND, before, ProjectConditionMode.NONE, List.of()));
        var other = persist(new Recruitment(project, ProjectRole.DESIGN, 3, ProjectConditionMode.NONE, List.of()));
        int id = create(type, ProjectRole.BACKEND);
        resolve(type.equals("APPLY") ? leader : outsider, id, "ACCEPTED", 200);
        resolve(type.equals("APPLY") ? leader : outsider, id, "ACCEPTED", 409);
        em.flush(); em.clear();
        assertThat(em.find(Recruitment.class, getId(recruitment)).getRemainingCount()).isEqualTo(after);
        assertThat(em.find(Recruitment.class, getId(other)).getRemainingCount()).isEqualTo(3);
        mvc.perform(get(path()).session(session(leader))).andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitments[0].remainingCount").value(after));
    }

    @ParameterizedTest
    @CsvSource({"APPLY", "OFFER"})
    void absentRoleStillAllowsAcceptance(String type) throws Exception {
        int id = create(type, ProjectRole.AI);
        resolve(type.equals("APPLY") ? leader : outsider, id, "ACCEPTED", 200);
        assertThat(em.createQuery("select count(r) from Recruitment r where r.project.id = :id", Long.class)
                .setParameter("id", project.getId()).getSingleResult()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"APPLY,REJECTED", "APPLY,CANCELED", "OFFER,REJECTED", "OFFER,CANCELED"})
    void rejectionAndCancellationDoNotChangeRemainingCount(String type, String result) throws Exception {
        var recruitment = persist(new Recruitment(project, ProjectRole.BACKEND, 2, ProjectConditionMode.NONE, List.of()));
        int id = create(type, ProjectRole.BACKEND);
        var actor = result.equals("CANCELED") ? (type.equals("APPLY") ? outsider : leader)
                : (type.equals("APPLY") ? leader : outsider);
        resolve(actor, id, result, 200);
        em.flush(); em.clear();
        assertThat(em.find(Recruitment.class, getId(recruitment)).getRemainingCount()).isEqualTo(2);
    }

    @Test
    void acceptedApplicationSharesContactsWithCurrentLeaderOnlyIncludingPrivateProfiles() throws Exception {
        candidate.replaceContacts(List.of(new ProfileContact("DISCORD", "contact-secret"),
                new ProfileContact("email", "candidate@example.com")));
        int id = create("APPLY", ProjectRole.BACKEND);
        contact(leader, candidate, 403, null);
        contact(outsider, leaderProfile, 200, null);
        resolve(leader, id, "ACCEPTED", 200);
        contact(leader, candidate, 200, "contact-secret");
        mvc.perform(get("/profiles/" + candidate.getId()).session(session(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contacts.length()").value(2))
                .andExpect(jsonPath("$.contacts[1].value").value("candidate@example.com"));
        contact(outsider, leaderProfile, 200, "contact-secret");
        contact(member, candidate, 403, null);
        mvc.perform(post(path() + "/start").session(session(leader))).andExpect(status().isOk());
        mvc.perform(patch(path() + "/leader").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetProfileId\":" + memberProfile.getId() + "}")).andExpect(status().isNoContent());
        contact(leader, candidate, 403, null);
        contact(member, candidate, 200, "contact-secret");
        contact(outsider, memberProfile, 200, "contact-secret");
        contact(outsider, leaderProfile, 200, null);
    }

    @Test
    void acceptedOfferSharesContactsWithOriginalSenderNotTheirSuccessor() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        int id = create("OFFER", ProjectRole.BACKEND);
        resolve(outsider, id, "ACCEPTED", 200);
        contact(leader, candidate, 200, "contact-secret");
        contact(outsider, leaderProfile, 200, "contact-secret");
        mvc.perform(patch(path() + "/leader").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetProfileId\":" + memberProfile.getId() + "}")).andExpect(status().isNoContent());
        contact(leader, candidate, 200, "contact-secret");
        contact(outsider, leaderProfile, 200, "contact-secret");
        contact(member, candidate, 403, null);
        contact(outsider, memberProfile, 403, null);
    }

    @ParameterizedTest
    @CsvSource({"APPLY,REJECTED", "APPLY,CANCELED", "OFFER,REJECTED", "OFFER,CANCELED"})
    void nonAcceptedRequestsNeverDiscloseContacts(String type, String result) throws Exception {
        int id = create(type, ProjectRole.BACKEND);
        var actor = result.equals("CANCELED") ? (type.equals("APPLY") ? outsider : leader)
                : (type.equals("APPLY") ? leader : outsider);
        resolve(actor, id, result, 200);
        contact(outsider, leaderProfile, 200, null);
        contact(leader, candidate, 403, null);
    }

    @Test
    void deletedProjectDoesNotKeepContactAccess() throws Exception {
        int id = create("OFFER", ProjectRole.BACKEND);
        resolve(outsider, id, "ACCEPTED", 200);
        contact(outsider, leaderProfile, 200, "contact-secret");
        mvc.perform(delete(path()).session(session(leader))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        contact(outsider, leaderProfile, 200, null);
        contact(leader, candidate, 403, null);
    }

    @Test
    void publicProfileContactFieldsAreExplicitlyNullForUnrelatedViewer() throws Exception {
        mvc.perform(get("/profiles/" + leaderProfile.getId()).session(session(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactPlatform").value(nullValue()))
                .andExpect(jsonPath("$.contactId").value(nullValue()))
                .andExpect(jsonPath("$.contacts").isEmpty());
    }

    private int create(String type, ProjectRole role) throws Exception {
        String json = type.equals("APPLY") ? "{\"type\":\"APPLY\",\"role\":\"" + role + "\"}"
                : "{\"type\":\"OFFER\",\"role\":\"" + role + "\",\"targetProfileId\":" + candidate.getId() + "}";
        String response = mvc.perform(post(path() + "/participation-requests").session(session(type.equals("APPLY") ? outsider : leader))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.requestId");
    }
    private void resolve(com.gopick.server.account.domain.Account actor, int id, String value, int expected) throws Exception {
        mvc.perform(patch(path() + "/participation-requests/" + id).session(session(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + value + "\"}"))
                .andExpect(status().is(expected));
    }
    private void contact(com.gopick.server.account.domain.Account viewer, Profile target, int code, String expected) throws Exception {
        var result = mvc.perform(get("/profiles/" + target.getId()).session(session(viewer))).andExpect(status().is(code));
        if (code == 200 && expected != null) result.andExpect(jsonPath("$.contactPlatform").value("DISCORD")).andExpect(jsonPath("$.contactId").value(expected));
        else result.andExpect(jsonPath("$.contactId").doesNotExist()).andExpect(jsonPath("$.contactPlatform").doesNotExist());
    }
    private Long getId(Object entity) { return (Long) em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity); }
}
