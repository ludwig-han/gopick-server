package com.gopick.server.project.repository;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProjectSearchRepository extends Repository<Project, Long> {
    @Query("""
            select p from Project p
            where (:status is null or p.status = :status)
              and (:recruiting is null or p.recruiting = :recruiting)
            order by p.id desc
            """)
    List<Project> search(@Param("status") ProjectStatus status, @Param("recruiting") Boolean recruiting);
}
