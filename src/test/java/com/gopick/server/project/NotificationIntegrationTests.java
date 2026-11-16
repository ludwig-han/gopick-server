package com.gopick.server.project;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationIntegrationTests extends ProjectApiFixture {
    @ParameterizedTest
    @CsvSource({"APPLY,ACCEPTED,APPLY_ACCEPTED", "APPLY,REJECTED,APPLY_REJECTED", "OFFER,ACCEPTED,OFFER_ACCEPTED", "OFFER,REJECTED,OFFER_REJECTED"})
    void requestNotificationsReachCorrectAccounts(String type, String resolution, String resultType) throws Exception {
        var candidate = profile(outsider, true);
        String body = type.equals("APPLY") ? "{\"type\":\"APPLY\",\"role\":\"BACKEND\"}" :
                "{\"type\":\"OFFER\",\"role\":\"BACKEND\",\"targetProfileId\":" + candidate.getId() + "}";
        var sender = type.equals("APPLY") ? outsider : leader;
        var receiver = type.equals("APPLY") ? leader : outsider;
        String response = mvc.perform(post(path()+"/participation-requests").session(session(sender))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int requestId = JsonPath.read(response, "$.requestId");
        mvc.perform(get("/me/notifications").session(session(receiver))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value(type+"_RECEIVED"))
                .andExpect(jsonPath("$[0].requestId").value(requestId))
                .andExpect(jsonPath("$[0].requestAvailable").value(true));
        mvc.perform(patch(path()+"/participation-requests/"+requestId).session(session(receiver))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\""+resolution+"\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/me/notifications").session(session(sender))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value(resultType))
                .andExpect(jsonPath("$[0].requestAvailable").value(true));
        mvc.perform(get("/me/notifications").session(session(member))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
    @Test
    void deletedRequestLeavesNotificationButIsUnavailable() throws Exception {
        profile(outsider, true);
        mvc.perform(post(path() + "/participation-requests").session(session(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"APPLY\",\"role\":\"BACKEND\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/me/notifications").session(session(leader)))
                .andExpect(jsonPath("$[0].requestAvailable").value(true));
        mvc.perform(delete("/me").session(session(outsider))).andExpect(status().isNoContent());
        mvc.perform(get("/me/notifications").session(session(leader)))
                .andExpect(jsonPath("$[0].type").value("APPLY_RECEIVED"))
                .andExpect(jsonPath("$[0].requestAvailable").value(false));
    }

    @Test
    void applicationNotificationBecomesUnavailableAfterLeaderTransfer() throws Exception {
        profile(outsider, true);
        mvc.perform(post(path() + "/participation-requests").session(session(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"APPLY\",\"role\":\"BACKEND\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post(path() + "/start").session(session(leader))).andExpect(status().isOk());
        mvc.perform(patch(path() + "/leader").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetProfileId\":" + memberProfile.getId() + "}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/me/notifications").session(session(leader)))
                .andExpect(jsonPath("$[0].type").value("APPLY_RECEIVED"))
                .andExpect(jsonPath("$[0].requestAvailable").value(false));
        mvc.perform(get("/me/participation-requests/received").session(session(member)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deletedProjectMakesRequestUnavailable() throws Exception {
        profile(outsider, true);
        mvc.perform(post(path() + "/participation-requests").session(session(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"APPLY\",\"role\":\"BACKEND\"}"))
                .andExpect(status().isCreated());
        mvc.perform(delete(path()).session(session(leader))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get("/me/notifications").session(session(leader)))
                .andExpect(jsonPath("$[0].requestAvailable").value(false));
    }
    @Test
    void readIsPrivateIdempotentAndRequiresAuthentication() throws Exception {
        profile(outsider, true);
        mvc.perform(post(path() + "/participation-requests").session(session(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"APPLY\",\"role\":\"BACKEND\"}"))
                .andExpect(status().isCreated());
        var leaderSession = session(leader);
        String listPath = "/me/notifications";
        String body = mvc.perform(get(listPath).session(leaderSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].readAt").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$[0].id");
        String readPath = listPath + "/" + id + "/read";

        mvc.perform(patch(readPath).session(session(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mvc.perform(patch(listPath + "/{notificationId}/read", Long.MAX_VALUE).session(leaderSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mvc.perform(get(listPath)).andExpect(status().isUnauthorized());
        mvc.perform(patch(listPath + "/invalid/read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(patch(readPath).session(leaderSession)).andExpect(status().isNoContent());
        String readBody = mvc.perform(get(listPath).session(leaderSession))
                .andExpect(jsonPath("$[0].read").value(true))
                .andExpect(jsonPath("$[0].readAt").isString())
                .andReturn().getResponse().getContentAsString();
        String firstReadAt = JsonPath.read(readBody, "$[0].readAt");

        mvc.perform(patch(readPath).session(leaderSession)).andExpect(status().isNoContent());
        mvc.perform(get(listPath).session(leaderSession))
                .andExpect(jsonPath("$[0].readAt").value(firstReadAt));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "+1", "bad", "9223372036854775808"})
    void invalidNotificationIdsReturnValidationErrors(String notificationId) throws Exception {
        mvc.perform(patch("/me/notifications/{notificationId}/read", notificationId).session(session(leader)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.notificationId").isString());
    }

    @Test
    void offerResponseGoesToOriginalSenderAfterLeaderTransfer() throws Exception {
        var candidate = profile(outsider, true);
        mvc.perform(post(path()+"/start").session(session(leader))).andExpect(status().isOk());
        String response = mvc.perform(post(path()+"/participation-requests").session(session(leader))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OFFER\",\"role\":\"BACKEND\",\"targetProfileId\":"+candidate.getId()+"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int requestId = JsonPath.read(response, "$.requestId");
        mvc.perform(patch(path()+"/leader").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetProfileId\":"+memberProfile.getId()+"}")).andExpect(status().isNoContent());
        mvc.perform(patch(path()+"/participation-requests/"+requestId).session(session(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}")).andExpect(status().isOk());
        mvc.perform(get("/me/notifications").session(session(leader))).andExpect(jsonPath("$[0].type").value("OFFER_REJECTED"));
        mvc.perform(get("/me/notifications").session(session(member))).andExpect(jsonPath("$.length()").value(2));
    }
    @Test
    void stageChangeNotifiesOtherMembersOnlyOnceForSameStage() throws Exception {
        mvc.perform(post(path()+"/start").session(session(leader))).andExpect(status().isOk());
        for (int i=0; i<2; i++) {
            mvc.perform(patch(path()+"/stage").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"stage\":\"DESIGN\"}")).andExpect(status().isOk());
        }
        mvc.perform(get("/me/notifications").session(session(member)))
                .andExpect(jsonPath("$[0].type").value("PROJECT_STAGE_CHANGED"))
                .andExpect(jsonPath("$[0].requestAvailable").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/me/notifications").session(session(leader))).andExpect(jsonPath("$").isEmpty());
    }
    @Test
    void projectEventsNotifyMembersAndTransferTarget() throws Exception {
        mvc.perform(post(path()+"/start").session(session(leader))).andExpect(status().isOk());
        mvc.perform(patch(path()+"/leader").session(session(leader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetProfileId\":"+memberProfile.getId()+"}")).andExpect(status().isNoContent());
        mvc.perform(post(path()+"/complete").session(session(member))).andExpect(status().isOk());
        mvc.perform(get("/me/notifications").session(session(member))).andExpect(jsonPath("$[0].type").value("LEADER_TRANSFERRED"))
                .andExpect(jsonPath("$[1].type").value("PROJECT_STARTED"));
        mvc.perform(get("/me/notifications").session(session(leader))).andExpect(jsonPath("$[0].type").value("PROJECT_COMPLETED"));
        mvc.perform(post(path()+"/complete").session(session(member))).andExpect(status().isConflict());
        mvc.perform(get("/me/notifications").session(session(leader))).andExpect(jsonPath("$.length()").value(1));
    }
}
