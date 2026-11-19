package com.gopick.server.project.service;

import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.dto.RecommendationAiRequest;
import com.gopick.server.project.dto.RecommendationResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UpstageRecommendationExperienceTests {
    private static final String RESULT = """
            {"items":[{"profileId":7,"score":80,
              "matched":[{"category":"EXPERIENCE","text":"프로젝트 경험 일치"},
                         {"category":"ROLE","text":"역할 일치"},
                         {"category":"FIELD","text":"관련 경력 보유"}],
              "unmatched":[{"category":"EXPERIENCE","text":"경험 부족"}],
              "summary":"경험이 풍부하여 적합"}]}
            """;

    @Test
    void noneOmitsExperienceFromInputAndRemovesItFromBothReasonsAndSummary() {
        var transport = mock(UpstageChatClient.class);
        when(transport.complete(anyString(), anyString(), anyDouble(), anyInt(), anyMap())).thenReturn(RESULT);
        var client = new UpstageRecommendationAiClient(transport, new JsonMapper());

        var item = client.recommend(request(ProjectConditionMode.NONE)).items().getFirst();

        verify(transport).complete(contains("experienceMode가 NONE"), argThat(prompt ->
                !prompt.contains("secret-experience") && prompt.contains("\"experiences\":[]")),
                anyDouble(), anyInt(), anyMap());
        assertThat(item.matched()).extracting(RecommendationResponse.Reason::category)
                .containsExactly(RecommendationResponse.Category.ROLE);
        assertThat(item.unmatched()).isEmpty();
        assertThat(item.summary()).doesNotContain("경험", "경력").contains("1개");
    }

    @Test
    void preferredKeepsExperienceInputAndAiOutput() {
        var transport = mock(UpstageChatClient.class);
        when(transport.complete(anyString(), anyString(), anyDouble(), anyInt(), anyMap())).thenReturn(RESULT);
        var client = new UpstageRecommendationAiClient(transport, new JsonMapper());

        var item = client.recommend(request(ProjectConditionMode.PREFERRED)).items().getFirst();

        verify(transport).complete(anyString(), contains("secret-experience"), anyDouble(), anyInt(), anyMap());
        assertThat(item.matched().getFirst().category()).isEqualTo(RecommendationResponse.Category.EXPERIENCE);
        assertThat(item.summary()).contains("경험");
    }

    private RecommendationAiRequest request(ProjectConditionMode mode) {
        var project = new RecommendationAiRequest.ProjectData(1L, ProjectRole.BACKEND,
                "QA", "", "", "", "", ProjectConditionMode.NONE, List.of(), mode,
                List.of(), List.of(), List.of());
        var candidate = new RecommendationAiRequest.Candidate(7L, "candidate", "", "", "",
                List.of(), List.of("BACKEND"), List.of(), List.of(), List.of(),
                List.of(new RecommendationAiRequest.Experience("secret-experience", List.of())));
        return new RecommendationAiRequest(project, List.of(candidate));
    }
}
