package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfileExperience;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileExperienceRepository extends JpaRepository<ProfileExperience, Long> {
}
