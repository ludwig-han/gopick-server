package com.gopick.server.project.controller;

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
        return service.start(requireAccountId(request), projectId);
    }
    @PostMapping("/complete")
    public ProjectResponse complete(@PathVariable("projectId") String projectId, HttpServletRequest request) {
        return service.complete(requireAccountId(request), projectId);
    }

    @PatchMapping("/stage")
    public ProjectResponse stage(@PathVariable("projectId") String projectId,
                                 @Valid @RequestBody ProjectStageRequest update, HttpServletRequest request) {
        return service.changeStage(requireAccountId(request), projectId, update.stage());
    }
}
