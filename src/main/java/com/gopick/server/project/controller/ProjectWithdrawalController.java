package com.gopick.server.project.controller;

import com.gopick.server.project.service.ProjectWithdrawalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
public class ProjectWithdrawalController {
    private final ProjectWithdrawalService service;

    public ProjectWithdrawalController(ProjectWithdrawalService service) {
        this.service = service;
    }

    @DeleteMapping("/projects/{projectId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable("projectId") String projectId, HttpServletRequest request) {
        service.withdraw(requireAccountId(request), projectId);
    }
}
