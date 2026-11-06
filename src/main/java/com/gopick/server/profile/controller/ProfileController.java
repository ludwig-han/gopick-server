package com.gopick.server.profile.controller;

import com.gopick.server.account.exception.UnauthenticatedException;
import com.gopick.server.profile.dto.CreateProfileRequest;
import com.gopick.server.profile.dto.CreateProfileResponse;
import com.gopick.server.profile.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {

    private ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/profiles")
    public ResponseEntity<CreateProfileResponse> createProfile(@Valid @RequestBody CreateProfileRequest createProfileRequest, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("accountId") == null) {
            throw new UnauthenticatedException();
        }
        Long accountId = (Long) session.getAttribute("accountId");
        CreateProfileResponse createProfileResponse = profileService.createProfile(accountId, createProfileRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(createProfileResponse);
    }
}
