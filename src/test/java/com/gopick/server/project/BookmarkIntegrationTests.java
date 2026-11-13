package com.gopick.server.project;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookmarkIntegrationTests extends ProjectApiFixture {
    private static final String PROFILES = "/me/bookmarked-profiles";
    private static final String PROJECTS = "/me/scrapped-projects";

    @Test
    void profileBookmarksArePrivateAndCanBeAddedAndRemovedIdempotently() throws Exception {
        String target = profileBookmarkPath(leaderProfile.getId());
        var owner = session(outsider);
        var other = session(member);

        mvc.perform(post(target).session(owner)).andExpect(status().isNoContent());
        mvc.perform(post(target).session(owner)).andExpect(status().isNoContent());
        mvc.perform(get(PROFILES).session(other)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(delete(target).session(other)).andExpect(status().isNoContent());
        mvc.perform(get(PROFILES).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].profileId", contains(leaderProfile.getId().intValue())))
                .andExpect(jsonPath("$[0].name").value(leaderProfile.getName()))
                .andExpect(jsonPath("$[0].contactPlatform").doesNotExist())
                .andExpect(jsonPath("$[0].contactId").doesNotExist());

        mvc.perform(delete(target).session(owner)).andExpect(status().isNoContent());
        mvc.perform(delete(target).session(owner)).andExpect(status().isNoContent());
        mvc.perform(get(PROFILES).session(owner)).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aProfileBecomingPrivateKeepsTheBookmarkButHidesItsDetails() throws Exception {
        var owner = session(outsider);
        String target = profileBookmarkPath(leaderProfile.getId());
        mvc.perform(post(target).session(owner)).andExpect(status().isNoContent());
        leaderProfile.update("sensitive-name", "sensitive-major", "DISCORD", "sensitive-contact",
                false, "sensitive-tendency", "sensitive-intro");
        em.flush();
        em.clear();

        mvc.perform(get(PROFILES).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].profileId").value(leaderProfile.getId().intValue()))
                .andExpect(jsonPath("$[0].isPublic").value(false))
                .andExpect(jsonPath("$[0].name").value("비공개 프로필"))
                .andExpect(jsonPath("$[0].major").doesNotExist())
                .andExpect(jsonPath("$[0].intro").doesNotExist())
                .andExpect(jsonPath("$[0].contactId").doesNotExist())
                .andExpect(jsonPath("$[0].contactPlatform").doesNotExist());
        mvc.perform(post(target).session(session(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVATE_PROFILE"));
        mvc.perform(delete(target).session(owner)).andExpect(status().isNoContent());
    }

    @Test
    void projectScrapsArePrivateAndCanBeAddedAndRemovedIdempotently() throws Exception {
        String target = projectScrapPath(project.getId());
        var owner = session(outsider);
        var other = session(member);

        mvc.perform(post(target).session(owner)).andExpect(status().isNoContent());
        mvc.perform(post(target).session(owner)).andExpect(status().isNoContent());
        mvc.perform(get(PROJECTS).session(other)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(delete(target).session(other)).andExpect(status().isNoContent());
        mvc.perform(get(PROJECTS).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].projectId", contains(project.getId().intValue())))
                .andExpect(jsonPath("$[0].title").value(project.getTitle()));

        mvc.perform(delete(target).session(owner)).andExpect(status().isNoContent());
        mvc.perform(delete(target).session(owner)).andExpect(status().isNoContent());
        mvc.perform(get(PROJECTS).session(owner)).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void requiresLoginAndChecksThatSaveTargetsExist() throws Exception {
        mvc.perform(get(PROFILES)).andExpect(status().isUnauthorized());
        mvc.perform(get(PROJECTS)).andExpect(status().isUnauthorized());
        mvc.perform(post(profileBookmarkPath(Long.MAX_VALUE)).session(session(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
        mvc.perform(post(projectScrapPath(Long.MAX_VALUE)).session(session(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    private String profileBookmarkPath(Long profileId) {
        return "/profiles/" + profileId + "/bookmarks";
    }

    private String projectScrapPath(Long projectId) {
        return "/projects/" + projectId + "/scraps";
    }
}
