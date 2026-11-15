package com.gopick.server.project.controller;

import com.gopick.server.project.dto.CreateParticipationRequest;
import com.gopick.server.project.dto.ParticipationRequestResponse;
import com.gopick.server.project.dto.ResolveParticipationRequest;
import com.gopick.server.project.service.ParticipationRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
public class ParticipationRequestController {
    private final ParticipationRequestService service;

    public ParticipationRequestController(ParticipationRequestService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/participation-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestResponse create(@PathVariable("projectId") String projectId,
                                               @Valid @RequestBody CreateParticipationRequest input,
                                               HttpServletRequest request) {
        return service.create(requireAccountId(request), projectId, input);
    }

    @PatchMapping("/projects/{projectId}/participation-requests/{requestId}")
    public ParticipationRequestResponse resolve(@PathVariable("projectId") String projectId,
                                                @PathVariable("requestId") String requestId,
                                                @Valid @RequestBody ResolveParticipationRequest input,
                                                HttpServletRequest request) {
        return service.resolve(requireAccountId(request), projectId, requestId, input.status());
    }

    @GetMapping("/me/participation-requests/sent")
    public List<ParticipationRequestResponse> sent(HttpServletRequest request) {
        return service.sent(requireAccountId(request));
    }

    @GetMapping("/me/participation-requests/received")
    public List<ParticipationRequestResponse> received(HttpServletRequest request) {
        return service.received(requireAccountId(request));
    }
}
