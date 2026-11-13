package com.gopick.server.project.controller;

import com.gopick.server.project.dto.ProjectLeaderTransferRequest;
import com.gopick.server.project.service.ProjectLeaderTransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
public class ProjectLeaderTransferController {
    private final ProjectLeaderTransferService service;

    public ProjectLeaderTransferController(ProjectLeaderTransferService service) {
        this.service = service;
    }

    @PatchMapping("/projects/{projectId}/leader")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@PathVariable("projectId") String projectId,
                         @Valid @RequestBody ProjectLeaderTransferRequest update,
                         HttpServletRequest request) {
        service.transfer(requireAccountId(request), projectId, update.targetProfileId());
    }
}
