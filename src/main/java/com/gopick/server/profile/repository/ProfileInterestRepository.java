package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfileInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileInterestRepository extends JpaRepository<ProfileInterest, Long> {
    List<ProfileInterest> findAllByProfile_IdOrderByIdAsc(Long profileId);
}
