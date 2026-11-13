package com.gopick.server.project.controller;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.dto.ProjectCreateRequest;
import com.gopick.server.project.dto.ProjectCreateResponse;
import com.gopick.server.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
@RequestMapping("/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectCreateResponse> createProject(@Valid @RequestBody ProjectCreateRequest request, HttpServletRequest httpRequest) {
        Long accountId = requireAccountId(httpRequest);

        ProjectCreateResponse response = projectService.createProject(accountId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }
}
