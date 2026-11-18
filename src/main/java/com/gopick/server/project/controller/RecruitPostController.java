package com.gopick.server.project.controller;

import com.gopick.server.project.dto.RecruitPostDraftRequest;
import com.gopick.server.project.dto.RecruitPostDraftResponse;
import com.gopick.server.project.service.RecruitPostDraftService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
@RequestMapping("/projects")
public class RecruitPostController {

    private final RecruitPostDraftService service;

    public RecruitPostController(
            RecruitPostDraftService service
    ) {
        this.service = service;
    }

    @PostMapping("/recruit-post/draft")
    public RecruitPostDraftResponse generate(
            @Valid @RequestBody RecruitPostDraftRequest request,
            HttpServletRequest httpRequest
    ) {
        return service.generate(requireAccountId(httpRequest), request);
    }
}
