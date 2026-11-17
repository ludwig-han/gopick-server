package com.gopick.server.project.service;

import com.gopick.server.project.dto.RecruitPostDraftRequest;

public interface RecruitPostAiClient {

    String generate(RecruitPostDraftRequest request);
}