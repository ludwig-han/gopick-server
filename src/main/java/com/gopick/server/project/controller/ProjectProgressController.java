package com.gopick.server.project.controller;

import com.gopick.server.global.DatabaseDeadlockRetry;
import com.gopick.server.project.dto.ProjectResponse;
import com.gopick.server.project.dto.ProjectStageRequest;
import com.gopick.server.project.service.ProjectProgressService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
@RequestMapping("/projects/{projectId}")
public class ProjectProgressController {
    private final ProjectProgressService service;
    public ProjectProgressController(ProjectProgressService service) { this.service = service; }
    @PostMapping("/start")
    public ProjectResponse start(@PathVariable("projectId") String projectId, HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        return DatabaseDeadlockRetry.call(() -> service.start(accountId, projectId));
    }
    @PostMapping("/complete")
    public ProjectResponse complete(@PathVariable("projectId") String projectId, HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        return DatabaseDeadlockRetry.call(() -> service.complete(accountId, projectId));
    }

    @PatchMapping("/stage")
    public ProjectResponse stage(@PathVariable("projectId") String projectId,
                                 @Valid @RequestBody ProjectStageRequest update, HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        return DatabaseDeadlockRetry.call(() -> service.changeStage(accountId, projectId, update.stage()));
    }
}
