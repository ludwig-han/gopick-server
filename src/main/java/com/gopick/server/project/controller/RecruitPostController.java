package com.gopick.server.project.controller;

import com.gopick.server.project.dto.RecruitPostDraftRequest;
import com.gopick.server.project.dto.RecruitPostDraftResponse;
import com.gopick.server.project.service.RecruitPostDraftService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

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
            @RequestBody RecruitPostDraftRequest request,
            HttpServletRequest httpRequest
    ) {
        requireAccountId(httpRequest);

        return service.generate(request);
    }
}