package com.gopick.server.profile.service;

import com.gopick.server.account.exception.UnauthenticatedException;
import com.gopick.server.account.repository.AccountRepository;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileInterest;
import com.gopick.server.profile.domain.ProfileRole;
import com.gopick.server.profile.dto.ProfileDetailResponse.Technology;
import com.gopick.server.profile.dto.ProfileSearchResponse;
import com.gopick.server.profile.repository.ProfileInterestRepository;
import com.gopick.server.profile.repository.ProfileRepository;
import com.gopick.server.profile.repository.ProfileRoleRepository;
import com.gopick.server.profile.repository.ProfileTechnologyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Service
public class ProfileSearchService {
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final ProfileTechnologyRepository technologyRepository;
    private final ProfileInterestRepository interestRepository;
    private final ProfileRoleRepository roleRepository;

    public ProfileSearchService(AccountRepository accountRepository,
                                ProfileRepository profileRepository,
                                ProfileTechnologyRepository technologyRepository,
                                ProfileInterestRepository interestRepository,
                                ProfileRoleRepository roleRepository) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.technologyRepository = technologyRepository;
        this.interestRepository = interestRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<ProfileSearchResponse> search(Long accountId, String technology, String interest,
                                              String preferredRole) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new UnauthenticatedException();
        }

        List<Profile> profiles = profileRepository.searchPublicProfiles(
                normalize(technology), normalize(interest), normalize(preferredRole));
        if (profiles.isEmpty()) {
            return List.of();
        }

        List<Long> profileIds = profiles.stream().map(Profile::getId).toList();
        Map<Long, List<Technology>> technologies = technologyRepository.findAllByProfile_IdInOrderByIdAsc(profileIds)
                .stream().collect(groupingBy(t -> t.getProfile().getId(),
                        mapping(t -> new Technology(t.getTechnology(), t.getLevel()), toList())));
        Map<Long, List<String>> interests = interestRepository.findAllByProfile_IdInOrderByIdAsc(profileIds)
                .stream().collect(groupingBy(i -> i.getProfile().getId(), mapping(ProfileInterest::getField, toList())));
        Map<Long, List<String>> roles = roleRepository.findAllByProfile_IdInOrderByIdAsc(profileIds)
                .stream().collect(groupingBy(r -> r.getProfile().getId(), mapping(ProfileRole::getRole, toList())));

        return profiles.stream().map(profile -> new ProfileSearchResponse(
                profile.getId(), profile.getName(), profile.getMajor(), profile.getIntro(),
                technologies.getOrDefault(profile.getId(), List.of()),
                interests.getOrDefault(profile.getId(), List.of()),
                roles.getOrDefault(profile.getId(), List.of()))).toList();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
