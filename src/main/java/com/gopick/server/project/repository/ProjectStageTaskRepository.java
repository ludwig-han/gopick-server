package com.gopick.server.project.repository;

import com.gopick.server.project.domain.ProjectStageTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectStageTaskRepository extends JpaRepository<ProjectStageTask, Long> {
    List<ProjectStageTask> findAllByProject_IdOrderByIdAsc(Long projectId);
    Optional<ProjectStageTask> findByIdAndProject_Id(Long id, Long projectId);
}
