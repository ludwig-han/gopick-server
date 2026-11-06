package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfileTechnology;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileTechnologyRepository extends JpaRepository<ProfileTechnology, Long> {
}
