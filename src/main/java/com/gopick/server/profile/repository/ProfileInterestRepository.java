package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfileInterest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileInterestRepository extends JpaRepository<ProfileInterest, Long> {
}
