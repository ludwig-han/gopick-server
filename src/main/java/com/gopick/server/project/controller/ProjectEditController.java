package com.gopick.server.project.controller;

import com.gopick.server.project.dto.ProjectResponse;
import com.gopick.server.project.dto.ProjectUpdateRequest;
import com.gopick.server.project.service.ProjectEditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
public class ProjectEditController {
    private final ProjectEditService service;
    public ProjectEditController(ProjectEditService service) { this.service = service; }
    @PatchMapping("/projects/{projectId}")
    public ProjectResponse update(@PathVariable("projectId") String projectId,
                                  @Valid @RequestBody ProjectUpdateRequest update, HttpServletRequest request) {
        return service.update(requireAccountId(request), projectId, update);
    }
}
