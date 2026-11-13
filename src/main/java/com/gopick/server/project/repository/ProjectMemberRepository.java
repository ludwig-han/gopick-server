package com.gopick.server.project.repository;

import com.gopick.server.project.domain.ProjectMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectMember> findByProject_IdAndProfile_Id(Long projectId, Long profileId);
}
