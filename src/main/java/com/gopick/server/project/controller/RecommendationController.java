package com.gopick.server.project.controller;

import com.gopick.server.project.dto.RecommendationResponse;
import com.gopick.server.project.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
@RequestMapping("/projects")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping("/{projectId}/recommendations")
    public RecommendationResponse recommend(
            @PathVariable String projectId,
            @RequestParam String role,
            HttpServletRequest request
    ) {
        return service.recommend(
                requireAccountId(request),
                projectId,
                role
        );
    }
}
