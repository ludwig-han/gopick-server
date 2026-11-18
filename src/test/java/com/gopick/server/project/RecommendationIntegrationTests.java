package com.gopick.server.project;

import com.gopick.server.account.domain.Account;
import com.gopick.server.profile.domain.ExperienceAttribute;
import com.gopick.server.profile.domain.ExperienceAttributeCategory;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileExperience;
import com.gopick.server.profile.domain.ProfileRole;
import com.gopick.server.profile.domain.ProfileTechnology;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.domain.Recruitment;
import com.gopick.server.project.domain.RecruitmentTechnology;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationIntegrationTests extends ProjectApiFixture {
    @Test
    void archivedMembersDoNotBreakRecommendationsOrLeaderAuthorization() throws Exception {
        project.setStatus(ProjectStatus.COMPLETED);
        project.setRecruiting(false);
        recruitment(ProjectConditionMode.NONE);
        var candidate = profile(outsider, true);
        em.flush();

        mvc.perform(delete("/me").session(session(member))).andExpect(status().isNoContent());
        mvc.perform(get(path() + "/recommendations").param("role", "BACKEND").session(session(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].profileId").value(candidate.getId().intValue()));

        mvc.perform(delete("/me").session(session(leader))).andExpect(status().isNoContent());
        mvc.perform(get(path() + "/recommendations").param("role", "BACKEND").session(session(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
    }

    @Test
    void omittedExperienceDoesNotInfluenceReasonsOrRank() throws Exception {
        recruitment(ProjectConditionMode.NONE);
        var roleMatch = profile(outsider, true);
        persist(new ProfileRole(roleMatch, "BACKEND"));
        var unrelated = candidate("unrelated");
        persist(new ProfileExperience(unrelated, "Unrelated experience"));
        em.flush();
        em.clear();

        mvc.perform(get(path() + "/recommendations").param("role", "BACKEND").session(session(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].profileId").value(roleMatch.getId().intValue()))
                .andExpect(jsonPath("$.items[0].unmatched").isEmpty())
                .andExpect(jsonPath("$.items[1].profileId").value(unrelated.getId().intValue()))
                .andExpect(jsonPath("$.items[1].matched").isEmpty());
    }

    @Test
    void choosesNewestTwentyEligibleProfilesEvenBeyondFirstPage() throws Exception {
        recruitment(ProjectConditionMode.REQUIRED,
                new RecruitmentTechnology("Java", 3), new RecruitmentTechnology("Spring", 2));
        project.updateExperience(ProjectConditionMode.REQUIRED, List.of("TEAM"), List.of("WEB"));
        List<Integer> eligible = new ArrayList<>();
        for (int index = 0; index < 22; index++) {
            var profile = candidate("eligible-" + index);
            persist(new ProfileTechnology(profile, "java", 1));
            addRequiredTechnologies(profile);
            addExperience(profile, "  TeAm  ", "  Web  ");
            eligible.add(profile.getId().intValue());
        }

        var splitExperience = candidate("split-experience");
        addRequiredTechnologies(splitExperience);
        addExperience(splitExperience, "TEAM", "OTHER");
        addExperience(splitExperience, "OTHER", "WEB");

        var belowRequiredLevel = candidate("below-required-level");
        persist(new ProfileTechnology(belowRequiredLevel, "Java", 2));
        persist(new ProfileTechnology(belowRequiredLevel, "Spring", 2));
        addExperience(belowRequiredLevel, "TEAM", "WEB");

        for (int index = 0; index < 100; index++) {
            candidate("ineligible-" + index);
        }
        em.flush();
        em.clear();

        var expectedIds = eligible.reversed().subList(0, 20).toArray(Integer[]::new);
        mvc.perform(get(path() + "/recommendations").param("role", "BACKEND").session(session(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].profileId", contains(expectedIds)));
    }

    private void recruitment(ProjectConditionMode techMode, RecruitmentTechnology... technologies) {
        persist(new Recruitment(project, ProjectRole.BACKEND, 1, techMode, List.of(technologies)));
    }

    private Profile candidate(String emailPrefix) {
        return profile(persist(new Account(emailPrefix + "@example.com", "encoded-secret")), true);
    }

    private void addRequiredTechnologies(Profile profile) {
        persist(new ProfileTechnology(profile, "\t JAVA \t", 3));
        persist(new ProfileTechnology(profile, "Spring", 2));
    }

    private void addExperience(Profile profile, String type, String field) {
        var experience = persist(new ProfileExperience(profile, "Project experience"));
        persist(new ExperienceAttribute(experience, ExperienceAttributeCategory.TYPE, type));
        persist(new ExperienceAttribute(experience, ExperienceAttributeCategory.FIELD, field));
    }
}
