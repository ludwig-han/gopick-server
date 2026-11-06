package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfilePart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfilePartRepository extends JpaRepository<ProfilePart, Long> {
}
