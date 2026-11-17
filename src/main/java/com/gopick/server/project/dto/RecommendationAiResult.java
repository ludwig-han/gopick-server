package com.gopick.server.project.dto;

import java.util.List;

public record RecommendationAiResult(
        List<Item> items
) {

    public record Item(
            Long profileId,
            int score,
            List<RecommendationResponse.Reason> matched,
            List<RecommendationResponse.Reason> unmatched,
            String summary
    ) {}
}