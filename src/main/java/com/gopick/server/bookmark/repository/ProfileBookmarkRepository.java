package com.gopick.server.bookmark.repository;

import com.gopick.server.bookmark.domain.ProfileBookmark;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileBookmarkRepository extends JpaRepository<ProfileBookmark, Long> {
    @EntityGraph(attributePaths = "profile")
    List<ProfileBookmark> findAllByAccount_IdOrderByIdDesc(Long accountId);

    boolean existsByAccount_IdAndProfile_Id(Long accountId, Long profileId);

    void deleteByAccount_IdAndProfile_Id(Long accountId, Long profileId);
}
