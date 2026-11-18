package com.gopick.server.project.service;

import com.gopick.server.project.dto.RecruitPostDraftRequest;
import com.gopick.server.project.dto.RecruitPostDraftResponse;
import org.springframework.stereotype.Service;

@Service
public class RecruitPostDraftService {

    private final ProjectAccess access;
    private final RecruitPostAiClient aiClient;

    public RecruitPostDraftService(ProjectAccess access, RecruitPostAiClient aiClient) {
        this.access = access;
        this.aiClient = aiClient;
    }

    public RecruitPostDraftResponse generate(Long accountId, RecruitPostDraftRequest request) {
        access.requireAccount(accountId);
        return new RecruitPostDraftResponse(aiClient.generate(request));
    }
}
