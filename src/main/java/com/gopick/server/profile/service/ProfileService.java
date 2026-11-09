package com.gopick.server.profile.service;

import com.gopick.server.account.domain.Account;
import com.gopick.server.account.exception.UnauthenticatedException;
import com.gopick.server.account.repository.AccountRepository;
import com.gopick.server.profile.domain.ExperienceAttribute;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileExperience;
import com.gopick.server.profile.domain.ProfileInterest;
import com.gopick.server.profile.domain.ProfilePart;
import com.gopick.server.profile.domain.ProfileRole;
import com.gopick.server.profile.domain.ProfileTechnology;
import com.gopick.server.profile.dto.CreateProfileRequest;
import com.gopick.server.profile.dto.CreateProfileResponse;
import com.gopick.server.profile.dto.ProfileDetailResponse;
import com.gopick.server.profile.dto.ProfileTechnologyRequest;
import com.gopick.server.profile.exception.DuplicateProfileException;
import com.gopick.server.profile.exception.InvalidProfileIdException;
import com.gopick.server.profile.exception.PrivateProfileException;
import com.gopick.server.profile.exception.ProfileNotFoundException;
import com.gopick.server.profile.repository.ProfileExperienceAttributeRepository;
import com.gopick.server.profile.repository.ProfileExperienceRepository;
import com.gopick.server.profile.repository.ProfileInterestRepository;
import com.gopick.server.profile.repository.ProfilePartRepository;
import com.gopick.server.profile.repository.ProfilePreferenceRepository;
import com.gopick.server.profile.repository.ProfileRepository;
import com.gopick.server.profile.repository.ProfileRoleRepository;
import com.gopick.server.profile.repository.ProfileTechnologyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            ProfileTechnology profileTechnology = new ProfileTechnology(
                    savedProfile, technologyRequest.getTechnology(), technologyRequest.getLevel());
            profileTechnologyRepository.save(profileTechnology);
        }

        return new CreateProfileResponse(savedProfile.getId());
    }

    @Transactional(readOnly = true)
    public ProfileDetailResponse getProfileDetail(Long accountId, String rawProfileId) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new UnauthenticatedException();
        }
        Long profileId = parseProfileId(rawProfileId);

        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(ProfileNotFoundException::new);

        if (!profile.isPublic() && !profile.getAccount().getId().equals(accountId)) {
            throw new PrivateProfileException();
        }

        List<ProfileExperience> experiences = profileExperienceRepository
                .findAllByProfile_IdOrderByIdAsc(profileId);
        List<Long> experienceIds = experiences.stream()
                .map(ProfileExperience::getId)
                .toList();
        Map<Long, List<ExperienceAttribute>> attributesByExperience = experienceIds.isEmpty()
                ? Map.of()
                : profileExperienceAttributeRepository
                        .findAllByExperience_IdInOrderByIdAsc(experienceIds).stream()
                        .collect(Collectors.groupingBy(attribute -> attribute.getExperience().getId()));

        List<String> parts = profilePartRepository.findAllByProfile_IdOrderByIdAsc(profileId).stream()
                .map(ProfilePart::getPart)
                .toList();
        List<ProfileDetailResponse.Technology> technologies = profileTechnologyRepository
                .findAllByProfile_IdOrderByIdAsc(profileId).stream()
                .map(technology -> new ProfileDetailResponse.Technology(
                        technology.getTechnology(), technology.getLevel()))
                .toList();
        List<String> interests = profileInterestRepository.findAllByProfile_IdOrderByIdAsc(profileId).stream()
                .map(ProfileInterest::getField)
                .toList();
        List<String> preferredRoles = profileRoleRepository.findAllByProfile_IdOrderByIdAsc(profileId).stream()
                .map(ProfileRole::getRole)
                .toList();
        List<ProfileDetailResponse.Preference> preferences = profilePreferenceRepository
                .findAllByProfile_IdOrderByIdAsc(profileId).stream()
                .map(preference -> new ProfileDetailResponse.Preference(
                        preference.getCategory(), preference.getValue()))
                .toList();
        List<ProfileDetailResponse.Experience> experienceDetails = experiences.stream()
                .map(experience -> toExperienceDetail(experience, attributesByExperience))
                .toList();

        return new ProfileDetailResponse(
                profile.getId(),
                profile.getName(),
                profile.getMajor(),
                profile.getContactPlatform(),
                profile.isPublic(),
                profile.getTeamTendency(),
                profile.getIntro(),
                parts,
                technologies,
                interests,
                preferredRoles,
                preferences,
                experienceDetails
        );
    }

    private Long parseProfileId(String rawProfileId) {
        try {
            long profileId = Long.parseLong(rawProfileId);
            if (profileId > 0) {
                return profileId;
            }
        } catch (NumberFormatException ignored) {
            // Invalid and out-of-range IDs use the same validation response.
        }
        throw new InvalidProfileIdException();
    }

    private ProfileDetailResponse.Experience toExperienceDetail(
            ProfileExperience experience, Map<Long, List<ExperienceAttribute>> attributesByExperience) {
        List<ProfileDetailResponse.Attribute> attributes = attributesByExperience
                .getOrDefault(experience.getId(), List.of()).stream()
                .map(attribute -> new ProfileDetailResponse.Attribute(
                        attribute.getCategory(), attribute.getValue()))
                .toList();
        return new ProfileDetailResponse.Experience(
                experience.getId(), experience.getDescription(), attributes);
    }
}
