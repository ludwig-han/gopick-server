package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfilePreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfilePreferenceRepository extends JpaRepository<ProfilePreference, Long> {
}
