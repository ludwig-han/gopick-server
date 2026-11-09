package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfilePart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfilePartRepository extends JpaRepository<ProfilePart, Long> {
    List<ProfilePart> findAllByProfile_IdOrderByIdAsc(Long profileId);
    void deleteAllByProfile_Id(Long profileId);
}
