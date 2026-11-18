package com.gopick.server.project.repository;

import com.gopick.server.project.domain.ParticipationRequest;
import com.gopick.server.project.domain.ParticipationRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {
    // APPLY follows the current project leader; OFFER keeps the original sender as the counterpart.
    @Query("""
            select (count(r) > 0) from ParticipationRequest r join r.project project
            where r.status = com.gopick.server.project.domain.ParticipationRequestStatus.ACCEPTED
              and (
                (r.type = com.gopick.server.project.domain.ParticipationRequestType.OFFER and (
                    (r.sender.account.id = :viewer and r.participant.id = :target)
                    or (r.participant.account.id = :viewer and r.sender.id = :target)
                ))
                or (r.type = com.gopick.server.project.domain.ParticipationRequestType.APPLY and (
                    (r.participant.id = :target and exists (
                        select 1 from ProjectMember m where m.project = project
                        and m.memberType = com.gopick.server.project.domain.ProjectMemberType.LEADER
                        and m.profile.account.id = :viewer
                    ))
                    or (r.participant.account.id = :viewer and exists (
                        select 1 from ProjectMember m where m.project = project
                        and m.memberType = com.gopick.server.project.domain.ProjectMemberType.LEADER
                        and m.profile.id = :target
                    ))
                ))
              )
            """)
    boolean hasAcceptedContactAccess(@Param("viewer") Long viewer, @Param("target") Long target);

    // Mirrors the sent/received list visibility rules, restricted to IDs in the caller's notifications.
    @Query("""
            select r.id from ParticipationRequest r join r.project project
            where r.id in :ids and (
                r.sender.account.id = :accountId
                or (r.type = OFFER and r.participant.account.id = :accountId)
                or (r.type = APPLY and exists (
                    select 1 from ProjectMember m
                    where m.project = project and m.memberType = LEADER and m.profile.account.id = :accountId
                ))
            )
            """)
    List<Long> findVisibleIds(@Param("accountId") Long accountId, @Param("ids") Collection<Long> ids);

    @Query("select r.participant.account.id from ParticipationRequest r where r.id = :id and r.project.id = :projectId")
    Optional<Long> findParticipantAccountId(@Param("id") Long id, @Param("projectId") Long projectId);

    // The project is locked first. Locking reads see writes committed by the previous holder of that lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParticipationRequest> findFirstByProject_IdAndParticipant_IdAndStatus(
            Long projectId, Long participantId, ParticipationRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParticipationRequest> findByIdAndProject_Id(Long id, Long projectId);

    @Query("""
            select r from ParticipationRequest r
            join fetch r.project project
            join fetch r.sender sender
            join fetch r.participant
            where sender.account.id = :accountId
            order by r.id desc
            """)
    List<ParticipationRequest> findSent(@Param("accountId") Long accountId);

    @Query("""
            select r from ParticipationRequest r
            join fetch r.project project
            join fetch r.sender
            join fetch r.participant participant
            where (r.type = OFFER and participant.account.id = :accountId)
               or (r.type = APPLY and exists (
                    select 1 from ProjectMember m
                    where m.project = project and m.memberType = LEADER and m.profile.account.id = :accountId
               ))
            order by r.id desc
            """)
    List<ParticipationRequest> findReceived(@Param("accountId") Long accountId);
}
