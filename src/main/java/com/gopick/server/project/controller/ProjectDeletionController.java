package com.gopick.server.project.controller;

import com.gopick.server.project.service.ProjectDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
public class ProjectDeletionController {
    private final ProjectDeletionService service;

    public ProjectDeletionController(ProjectDeletionService service) {
        this.service = service;
    }

    @DeleteMapping("/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("projectId") String projectId, HttpServletRequest request) {
        service.delete(requireAccountId(request), projectId);
    }
}
