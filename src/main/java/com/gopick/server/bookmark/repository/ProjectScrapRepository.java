package com.gopick.server.bookmark.repository;

import com.gopick.server.bookmark.domain.ProjectScrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectScrapRepository extends JpaRepository<ProjectScrap, Long> {
    @Query("""
            select s from ProjectScrap s join fetch s.project
            where s.account.id = :accountId order by s.id desc
            """)
    List<ProjectScrap> findAllByAccount_IdOrderByIdDesc(@Param("accountId") Long accountId);

    boolean existsByAccount_IdAndProject_Id(Long accountId, Long projectId);

    void deleteByAccount_IdAndProject_Id(Long accountId, Long projectId);
}
