package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    boolean existsByAccount_Id(Long accountId);
    Optional<Profile> findByAccount_Id(Long accountId);
}
