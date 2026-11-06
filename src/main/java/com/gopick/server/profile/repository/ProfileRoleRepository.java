package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfileRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRoleRepository extends JpaRepository<ProfileRole, Long> {
}
