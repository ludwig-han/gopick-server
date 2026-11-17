package com.gopick.server.project.service;

import com.gopick.server.project.dto.RecruitPostDraftRequest;
import com.gopick.server.project.dto.RecruitPostDraftResponse;
import org.springframework.stereotype.Service;

@Service
public class RecruitPostDraftService {

    private final RecruitPostAiClient aiClient;

    public RecruitPostDraftService(RecruitPostAiClient aiClient) {
        this.aiClient = aiClient;
    }

    public RecruitPostDraftResponse generate(
            RecruitPostDraftRequest request
    ) {
        return new RecruitPostDraftResponse(
                aiClient.generate(request)
        );
    }
}