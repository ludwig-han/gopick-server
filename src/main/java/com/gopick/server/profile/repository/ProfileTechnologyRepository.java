package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfileTechnology;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileTechnologyRepository extends JpaRepository<ProfileTechnology, Long> {
    List<ProfileTechnology> findAllByProfile_IdOrderByIdAsc(Long profileId);
    void deleteAllByProfile_Id(Long profileId);
}
