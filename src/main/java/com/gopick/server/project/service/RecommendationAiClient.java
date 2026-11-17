package com.gopick.server.project.service;

import com.gopick.server.project.dto.RecommendationAiRequest;
import com.gopick.server.project.dto.RecommendationAiResult;

public interface RecommendationAiClient {

    RecommendationAiResult recommend(
            RecommendationAiRequest request
    );
}