package com.gopick.server.project.repository;

import com.gopick.server.project.domain.ProjectStage;
import com.gopick.server.project.domain.ProjectStageTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectStageTaskRepository extends JpaRepository<ProjectStageTask, Long> {
    List<ProjectStageTask> findAllByProject_IdAndStageOrderByIdAsc(Long projectId, ProjectStage stage);
    Optional<ProjectStageTask> findByIdAndProject_IdAndStage(Long id, Long projectId, ProjectStage stage);
}
