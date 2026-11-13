package com.gopick.server.project.repository;

import com.gopick.server.project.domain.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecruitmentRepository extends JpaRepository<Recruitment, Long> {
    List<Recruitment> findAllByProject_IdOrderByRoleAsc(Long projectId);
}
