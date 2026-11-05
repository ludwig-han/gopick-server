package com.gopick.server.profile.service;

import com.gopick.server.profile.dto.CreateProfileRequest;
import com.gopick.server.profile.dto.CreateProfileResponse;
import com.gopick.server.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {
    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public CreateProfileResponse createProfile(CreateProfileRequest createProfileRequest) {
        
    }
}
