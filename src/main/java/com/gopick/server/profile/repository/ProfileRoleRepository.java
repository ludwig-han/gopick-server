package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ProfileRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileRoleRepository extends JpaRepository<ProfileRole, Long> {
    List<ProfileRole> findAllByProfile_IdOrderByIdAsc(Long profileId);
    List<ProfileRole> findAllByProfile_IdInOrderByIdAsc(List<Long> profileIds);
    void deleteAllByProfile_Id(Long profileId);
}
