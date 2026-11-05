package com.gopick.server.profile.controller;

import com.gopick.server.profile.dto.CreateProfileRequest;
import com.gopick.server.profile.dto.CreateProfileResponse;
import com.gopick.server.profile.service.ProfileService;
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
    public CreateProfileResponse createProfile(@RequestBody CreateProfileRequest createProfileRequest) {
        profileService.createProfile(CreateProfileRequest req);
    }
}
