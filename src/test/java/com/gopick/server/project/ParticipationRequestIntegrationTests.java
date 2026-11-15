package com.gopick.server.project;

import com.gopick.server.account.domain.Account;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.project.domain.ProjectStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParticipationRequestIntegrationTests extends ProjectApiFixture {
    private static final String APPLY = """
            {"type":"APPLY","role":"BACKEND"}
            """;

    private Profile candidate;

    @BeforeEach
    void createCandidateProfile() {
        candidate = profile(outsider, false);
    }

    @Test
    void leaderAcceptsApplicationAndCreatesMembershipWithRequestedRole() throws Exception {
        long id = requestId(create(outsider, APPLY)
                .andExpect(jsonPath("$.projectTitle").value("GoPick"))
                .andExpect(jsonPath("$.type").value("APPLY"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.participantProfileId").value(candidate.getId().intValue())));

        sent(outsider).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value((int) id));
        received(leader).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value((int) id))
                .andExpect(jsonPath("$[0].contactId").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
        received(member).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        received(outsider).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        resolve(leader, id, "ACCEPTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.resolvedAt").isString());
        mvc.perform(get("/me/projects").session(session(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myAuthority").value("MEMBER"))
                .andExpect(jsonPath("$[0].members[2].profileId").value(candidate.getId().intValue()))
                .andExpect(jsonPath("$[0].members[2].role").value("BACKEND"));
        resolve(leader, id, "ACCEPTED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTICIPATION_REQUEST_CONFLICT"));
    }

    @Test
    void onlyApplicationSenderCanCancelAndOnlyLeaderCanReject() throws Exception {
        long id = requestId(create(outsider, APPLY));
        resolve(member, id, "REJECTED").andExpect(status().isForbidden());
        resolve(outsider, id, "ACCEPTED").andExpect(status().isForbidden());
        resolve(leader, id, "CANCELED").andExpect(status().isForbidden());
        resolve(outsider, id, "CANCELED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        long retried = requestId(create(outsider, APPLY));
        resolve(leader, retried, "REJECTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        sent(outsider).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].status").value("CANCELED"));
    }

    @ParameterizedTest
    @CsvSource({"PREPARING,false", "IN_PROGRESS,false", "COMPLETED,false"})
    void applicationRequiresPreparingAndOpenRecruitment(ProjectStatus state, boolean recruiting) throws Exception {
        project.setStatus(state);
        project.setRecruiting(recruiting);
        create(outsider, APPLY).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
    }

    @Test
    void leaderCanOfferToSpecificProfileAfterRecruitmentCloses() throws Exception {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setRecruiting(false);
        create(member, offer(candidate)).andExpect(status().isForbidden());

        long id = requestId(create(leader, offer(candidate))
                .andExpect(jsonPath("$.type").value("OFFER"))
                .andExpect(jsonPath("$.senderProfileId").value(leaderProfile.getId().intValue()))
                .andExpect(jsonPath("$.participantProfileId").value(candidate.getId().intValue())));
        received(outsider).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value((int) id));
        sent(leader).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value((int) id));
        resolve(leader, id, "ACCEPTED").andExpect(status().isForbidden());
        resolve(member, id, "REJECTED").andExpect(status().isForbidden());
        resolve(outsider, id, "CANCELED").andExpect(status().isForbidden());

        resolve(outsider, id, "ACCEPTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.role").value("DESIGN"));
        mvc.perform(get("/me/projects").session(session(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myAuthority").value("MEMBER"))
                .andExpect(jsonPath("$[0].members[2].role").value("DESIGN"));
    }

    @Test
    void offerRecipientCanRejectAndOriginalSenderCanCancel() throws Exception {
        project.setRecruiting(false);
        long id = requestId(create(leader, offer(candidate)));
        resolve(outsider, id, "REJECTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        long retried = requestId(create(leader, offer(candidate)));
        resolve(leader, retried, "CANCELED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        received(outsider).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELED"))
                .andExpect(jsonPath("$[1].status").value("REJECTED"));
    }

    @Test
    void pendingRequestsConflictAcrossDirectionsAndMembersCannotJoinTwice() throws Exception {
        create(member, APPLY).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTICIPATION_REQUEST_CONFLICT"));
        create(leader, offer(memberProfile)).andExpect(status().isConflict());

        long offerId = requestId(create(leader, offer(candidate)));
        create(outsider, APPLY).andExpect(status().isConflict());
        resolve(leader, offerId, "CANCELED").andExpect(status().isOk());

        long applicationId = requestId(create(outsider, APPLY));
        create(leader, offer(candidate)).andExpect(status().isConflict());
        resolve(leader, applicationId, "ACCEPTED").andExpect(status().isOk());
        create(leader, offer(candidate)).andExpect(status().isConflict());
    }

    @Test
    void completedProjectCannotGainMembershipFromAnOutstandingRequest() throws Exception {
        long id = requestId(create(leader, offer(candidate)));
        project.setStatus(ProjectStatus.COMPLETED);
        project.setRecruiting(false);

        create(leader, offer(candidate)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
        resolve(outsider, id, "ACCEPTED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATE"));
        resolve(outsider, id, "REJECTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void applicationInboxAndReviewAuthorityFollowLeaderTransfer() throws Exception {
        long id = requestId(create(outsider, APPLY));
        mvc.perform(post(path() + "/start").session(session(leader))).andExpect(status().isOk());
        mvc.perform(patch(path() + "/leader").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfileId\":" + memberProfile.getId() + "}"))
                .andExpect(status().isNoContent());

        received(leader).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        received(member).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value((int) id));
        resolve(leader, id, "ACCEPTED").andExpect(status().isForbidden());
        resolve(member, id, "ACCEPTED").andExpect(status().isOk());
    }

    @Test
    void malformedRequestsAndCrossProjectIdsReturnClientErrors() throws Exception {
        create(leader, "{\"type\":\"OFFER\",\"role\":\"BACKEND\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetProfileId").exists());
        create(outsider, "{\"type\":\"APPLY\",\"role\":\"BACKEND\",\"targetProfileId\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetProfileId").exists());
        create(outsider, "{\"type\":\"APPLY\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
        create(leader, "{\"type\":\"OFFER\",\"role\":\"BACKEND\",\"targetProfileId\":9223372036854775807}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));

        long id = requestId(create(outsider, APPLY));
        resolve(leader, id, "PENDING").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
        mvc.perform(patch(requestPath() + "/bad").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.requestId").exists());
        var other = newProject("Other", ProjectStatus.PREPARING, true);
        mvc.perform(patch("/projects/" + other.getId() + "/participation-requests/" + id)
                        .session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTICIPATION_REQUEST_NOT_FOUND"));
    }

    @Test
    void endpointsRequireValidSessionAndApplicationRequiresProfile() throws Exception {
        mvc.perform(post(requestPath()).contentType(MediaType.APPLICATION_JSON).content(APPLY))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(requestPath() + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isUnauthorized());
        var stale = new MockHttpSession();
        stale.setAttribute("accountId", Long.MAX_VALUE);
        mvc.perform(get("/me/participation-requests/sent").session(stale)).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/participation-requests/received")).andExpect(status().isUnauthorized());

        var noProfile = persist(new Account("no-profile@example.com", "encoded-secret"));
        create(noProfile, APPLY).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
        sent(noProfile).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void physicalProjectDeletionCascadesThroughParticipationRequestForeignKey() throws Exception {
        var removable = newProject("Removable", ProjectStatus.PREPARING, true);
        long id = requestId(mvc.perform(post("/projects/" + removable.getId() + "/participation-requests")
                .session(session(outsider)).contentType(MediaType.APPLICATION_JSON).content(APPLY)));

        em.flush();
        em.clear();
        em.createNativeQuery("delete from project where id = :id")
                .setParameter("id", removable.getId()).executeUpdate();
        Object count = em.createNativeQuery("select count(*) from participation_request where id = :id")
                .setParameter("id", id).getSingleResult();
        assertThat(((Number) count).longValue()).isZero();
    }

    private String requestPath() {
        return path() + "/participation-requests";
    }

    private ResultActions create(Account actor, String body) throws Exception {
        return mvc.perform(post(requestPath()).session(session(actor)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions resolve(Account actor, long id, String result) throws Exception {
        return mvc.perform(patch(requestPath() + "/" + id).session(session(actor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + result + "\"}"));
    }

    private ResultActions sent(Account actor) throws Exception {
        return mvc.perform(get("/me/participation-requests/sent").session(session(actor)));
    }

    private ResultActions received(Account actor) throws Exception {
        return mvc.perform(get("/me/participation-requests/received").session(session(actor)));
    }

    private String offer(Profile profile) {
        return "{\"type\":\"OFFER\",\"role\":\"DESIGN\",\"targetProfileId\":" + profile.getId() + "}";
    }

    private long requestId(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.requestId")).longValue();
    }
}
