package com.gopick.server.project;

import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileContact;
import com.gopick.server.project.domain.ProjectRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileContactsIntegrationTests extends ProjectApiFixture {
    private static final String CONTACTS = """
            [{"platform":"이메일","value":"a@example.com"},
             {"platform":"링크드인","value":"linkedin.example/a"},
             {"platform":"GitHub","value":"github.example/a"}]
            """;

    @Test
    void createUpdateAndLegacyClientPreserveOrderedContacts() throws Exception {
        String response = mvc.perform(post("/profiles").session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"contacts\":" + CONTACTS)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(response, "$.profileId");
        em.flush(); em.clear();
        mvc.perform(get("/profiles/" + id).session(session(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contacts.length()").value(3))
                .andExpect(jsonPath("$.contacts[0].platform").value("이메일"))
                .andExpect(jsonPath("$.contacts[1].value").value("linkedin.example/a"))
                .andExpect(jsonPath("$.contacts[2].platform").value("GitHub"))
                .andExpect(jsonPath("$.contactId").value("a@example.com"));
        mvc.perform(get("/profiles/" + id).session(session(leader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contacts").isEmpty());

        mvc.perform(patch("/profiles/" + id).session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"contactPlatform\":\"이메일\",\"contactId\":\"new@example.com\"")))
                .andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get("/profiles/" + id).session(session(outsider)))
                .andExpect(jsonPath("$.contacts.length()").value(3))
                .andExpect(jsonPath("$.contacts[0].value").value("new@example.com"))
                .andExpect(jsonPath("$.contacts[1].platform").value("링크드인"));
        mvc.perform(patch("/profiles/" + id).session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"contactPlatform\":\"링크드인\",\"contactId\":\"other\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/profiles/" + id).session(session(outsider)))
                .andExpect(jsonPath("$.contacts.length()").value(3))
                .andExpect(jsonPath("$.contacts[1].value").value("linkedin.example/a"));
        mvc.perform(patch("/profiles/" + id).session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"contacts\":[{\"platform\":\"Discord\",\"value\":\"user\"}]")))
                .andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get("/profiles/" + id).session(session(outsider)))
                .andExpect(jsonPath("$.contacts.length()").value(1))
                .andExpect(jsonPath("$.contacts[0].platform").value("Discord"));
        mvc.perform(patch("/profiles/" + id).session(session(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"contacts\":[]"))).andExpect(status().isNoContent());
        mvc.perform(get("/profiles/" + id).session(session(outsider))).andExpect(jsonPath("$.contacts").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"platform\":\"a\",\"value\":\"1\"},{\"platform\":\"b\",\"value\":\"2\"},{\"platform\":\"c\",\"value\":\"3\"},{\"platform\":\"d\",\"value\":\"4\"}]",
            "[{\"platform\":\"Discord\",\"value\":\"1\"},{\"platform\":\" discord \",\"value\":\"2\"}]",
            "[{\"platform\":\" \",\"value\":\"1\"}]",
            "[{\"platform\":\"a\",\"value\":\" \"}]",
            "[null]"
    })
    void invalidContactsReturnValidationErrorOnCreateAndUpdate(String contacts) throws Exception {
        String json = body("\"contacts\":" + contacts);
        mvc.perform(post("/profiles").session(session(outsider)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(patch("/profiles/" + leaderProfile.getId()).session(session(leader))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void existingContactIsFirstAndAdditionalContactsAreRemovedOnWithdrawal() throws Exception {
        var candidate = profile(outsider, true);
        em.flush(); em.clear();
        mvc.perform(get("/profiles/" + candidate.getId()).session(session(outsider)))
                .andExpect(jsonPath("$.contacts.length()").value(1))
                .andExpect(jsonPath("$.contacts[0].platform").value("DISCORD"))
                .andExpect(jsonPath("$.contacts[0].value").value("contact-secret"));
        candidate = em.find(Profile.class, candidate.getId());
        candidate.replaceContacts(List.of(new ProfileContact("DISCORD", "old"), new ProfileContact("email", "new")));
        em.flush(); em.clear();
        mvc.perform(get("/profiles/" + candidate.getId()).session(session(outsider)))
                .andExpect(jsonPath("$.contacts[0].value").value("old"))
                .andExpect(jsonPath("$.contacts[1].value").value("new"));
        mvc.perform(delete("/me").session(session(outsider))).andExpect(status().isNoContent());
        em.flush();
        assertThat(((Number) em.createNativeQuery("select count(*) from profile_additional_contact where profile_id = :id")
                .setParameter("id", candidate.getId()).getSingleResult()).longValue()).isZero();
    }

    private String body(String contactFields) {
        return "{\"name\":\"Tester\",\"major\":\"CS\",\"isPublic\":true,"
                + "\"parts\":[\"BACKEND\"],\"interests\":[\"WEB\"],\"preferredRoles\":[\"BACKEND\"],"
                + "\"technologies\":[{\"technology\":\"Java\",\"level\":2}]," + contactFields + "}";
    }
}
