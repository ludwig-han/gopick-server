package com.gopick.server.profile.service;

import com.gopick.server.account.domain.Account;
import com.gopick.server.profile.domain.*;
import com.gopick.server.profile.repository.ProfileTechnologyRepository;
import com.gopick.server.profile.dto.ProfileTechnologyRequest;
import com.gopick.server.profile.exception.DuplicateProfileException;
import com.gopick.server.account.exception.UnauthenticatedException;
import com.gopick.server.account.repository.AccountRepository;
import com.gopick.server.profile.dto.CreateProfileRequest;
import com.gopick.server.profile.dto.CreateProfileResponse;
import com.gopick.server.profile.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {
    private final ProfileRepository profileRepository;
    private final AccountRepository accountRepository;
    private final ProfilePartRepository profilePartRepository;
    private final ProfileExperienceRepository profileExperienceRepository;
    private final ProfileTechnologyRepository profileTechnologyRepository;
    private final ProfileRoleRepository profileRoleRepository;
    private final ProfileExperienceAttributeRepository profileExperienceAttributeRepository;
    private final ProfileInterestRepository profileInterestRepository;
    private final ProfilePreferenceRepository profilePreferenceRepository;

    public ProfileService(ProfileRepository profileRepository,
                          AccountRepository accountRepository,
                          ProfilePartRepository profilePartRepository,
                          ProfileTechnologyRepository profileTechnologyRepository,
                          ProfileInterestRepository profileInterestRepository,
                          ProfileRoleRepository profileRoleRepository,
                          ProfilePreferenceRepository profilePreferenceRepository,
                          ProfileExperienceRepository profileExperienceRepository,
                          ProfileExperienceAttributeRepository profileExperienceAttributeRepository) {
        this.profileRepository = profileRepository;
        this.accountRepository = accountRepository;
        this.profilePartRepository = profilePartRepository;
        this.profileTechnologyRepository = profileTechnologyRepository;
        this.profileInterestRepository = profileInterestRepository;
        this.profileRoleRepository = profileRoleRepository;
        this.profilePreferenceRepository = profilePreferenceRepository;
        this.profileExperienceRepository = profileExperienceRepository;
        this.profileExperienceAttributeRepository = profileExperienceAttributeRepository;
    }

    @Transactional
    public CreateProfileResponse createProfile(Long accountId, CreateProfileRequest createProfileRequest) {
        Account account = accountRepository.findById(accountId).orElseThrow(UnauthenticatedException::new);

        if (profileRepository.existsByAccount_Id(accountId)) {
            throw new DuplicateProfileException();
        }

        Profile profile = new Profile(
                account,
                createProfileRequest.getName(),
                createProfileRequest.getMajor(),
                createProfileRequest.getContactPlatform(),
                createProfileRequest.getContactId(),
                createProfileRequest.getIsPublic(),
                createProfileRequest.getTeamTendency(),
                createProfileRequest.getIntro()
        );

        Profile savedProfile = profileRepository.save(profile);

        for (String part : createProfileRequest.getParts()) {
            ProfilePart profilePart = new ProfilePart(savedProfile, part);
            profilePartRepository.save(profilePart);
        }

        for (String interest : createProfileRequest.getInterests()) {
            ProfileInterest profileInterest = new ProfileInterest(savedProfile, interest);
            profileInterestRepository.save(profileInterest);
        }

        for (String role : createProfileRequest.getRoles()) {
            ProfileRole profileRole = new ProfileRole(savedProfile, role);
            profileRoleRepository.save(profileRole);
        }

        for (ProfileTechnologyRequest technologyRequest : createProfileRequest.getTechnologies()) {
            ProfileTechnology profileTechnology = new ProfileTechnology(savedProfile, technologyRequest.getTechnology(), technologyRequest.getLevel());
            profileTechnologyRepository.save(profileTechnology);
        }

        return new CreateProfileResponse(savedProfile.getId());
    }
}
