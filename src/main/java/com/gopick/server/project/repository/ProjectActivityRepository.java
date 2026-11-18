package com.gopick.server.project.repository;

import com.gopick.server.project.domain.ProjectActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectActivityRepository extends JpaRepository<ProjectActivity, Long> {
    @Query("select a from ProjectActivity a left join fetch a.author where a.project.id = :projectId order by a.id desc")
    List<ProjectActivity> findActivities(@Param("projectId") Long projectId);
}
