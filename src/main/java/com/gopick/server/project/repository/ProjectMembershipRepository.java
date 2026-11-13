package com.gopick.server.project.repository;

import com.gopick.server.project.domain.ProjectMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProjectMembershipRepository extends Repository<ProjectMember, Long> {
    // Permission checks follow the project lock and must see the current membership.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from ProjectMember m
            where m.project.id = :projectId and m.profile.id = (
                select p.id from Profile p where p.account.id = :accountId
            )
            """)
    Optional<ProjectMember> findMembership(@Param("projectId") Long projectId, @Param("accountId") Long accountId);

    @Query("""
            select m from ProjectMember m join fetch m.profile p join fetch p.account
            where m.project.id = :projectId order by m.id
            """)
    List<ProjectMember> findMembers(@Param("projectId") Long projectId);
}
