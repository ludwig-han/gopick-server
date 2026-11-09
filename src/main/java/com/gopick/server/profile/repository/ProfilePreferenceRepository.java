package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfilePreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfilePreferenceRepository extends JpaRepository<ProfilePreference, Long> {
    List<ProfilePreference> findAllByProfile_IdOrderByIdAsc(Long profileId);
    void deleteAllByProfile_Id(Long profileId);
}
