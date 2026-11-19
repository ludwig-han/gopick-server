package com.gopick.server.project;

import com.gopick.server.profile.domain.ProfileExperience;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.Recruitment;
import com.gopick.server.project.dto.RecommendationAiRequest;
import com.gopick.server.project.dto.RecommendationAiResult;
import com.gopick.server.project.dto.RecommendationResponse;
import com.gopick.server.project.service.RecommendationAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationExperiencePolicyIntegrationTests extends ProjectApiFixture {
    @MockitoBean private RecommendationAiClient ai;

    @Test
    void noneIsEnforcedAtServiceBoundaryForAnyAiProvider() throws Exception {
        persist(new Recruitment(project, ProjectRole.BACKEND, 1, ProjectConditionMode.NONE, List.of()));
        var candidate = profile(outsider, true);
        persist(new ProfileExperience(candidate, "experience must not reach AI"));
        em.flush();
        when(ai.recommend(any())).thenAnswer(invocation -> {
            RecommendationAiRequest request = invocation.getArgument(0);
            assertThat(request.candidates()).hasSize(1);
            assertThat(request.candidates().getFirst().experiences()).isEmpty();
            var experience = new RecommendationResponse.Reason(RecommendationResponse.Category.EXPERIENCE, "경험 일치");
            var role = new RecommendationResponse.Reason(RecommendationResponse.Category.ROLE, "역할 일치");
            return new RecommendationAiResult(List.of(new RecommendationAiResult.Item(
                    candidate.getId(), 90, List.of(experience, role), List.of(experience), "경험이 풍부함")));
        });

        mvc.perform(get(path() + "/recommendations").param("role", "BACKEND").session(session(leader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].matched.length()").value(1))
                .andExpect(jsonPath("$.items[0].matched[0].category").value("ROLE"))
                .andExpect(jsonPath("$.items[0].unmatched").isEmpty())
                .andExpect(jsonPath("$.items[0].summary").value("일치 항목 1개, 확인이 필요한 항목 0개입니다."));
    }
}
