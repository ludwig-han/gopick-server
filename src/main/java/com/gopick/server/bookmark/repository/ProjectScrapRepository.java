package com.gopick.server.bookmark.repository;

import com.gopick.server.bookmark.domain.ProjectScrap;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectScrapRepository extends JpaRepository<ProjectScrap, Long> {
    @EntityGraph(attributePaths = "project")
    List<ProjectScrap> findAllByAccount_IdOrderByIdDesc(Long accountId);

    boolean existsByAccount_IdAndProject_Id(Long accountId, Long projectId);

    void deleteByAccount_IdAndProject_Id(Long accountId, Long projectId);
}
