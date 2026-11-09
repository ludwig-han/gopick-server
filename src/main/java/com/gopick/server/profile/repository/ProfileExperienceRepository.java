package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfileExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileExperienceRepository extends JpaRepository<ProfileExperience, Long> {
    List<ProfileExperience> findAllByProfile_IdOrderByIdAsc(Long profileId);
    void deleteAllByProfile_Id(Long profileId);
}
