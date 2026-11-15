package com.gopick.server.project.repository;

import com.gopick.server.project.domain.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecruitmentRepository extends JpaRepository<Recruitment, Long> {
    List<Recruitment> findAllByProject_IdOrderByRoleAsc(Long projectId);

    @Query("""
            select distinct r from Recruitment r left join fetch r.technologies
            where r.project.id in :ids order by r.role
            """)
    List<Recruitment> findForProjects(@Param("ids") List<Long> ids);
}
