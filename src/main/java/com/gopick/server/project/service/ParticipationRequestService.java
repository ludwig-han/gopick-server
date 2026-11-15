package com.gopick.server.project.service;

import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import com.gopick.server.project.repository.ProjectMembershipRepository;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.exception.ProfileNotFoundException;
import com.gopick.server.profile.repository.ProfileRepository;
import com.gopick.server.project.domain.ParticipationRequest;
import com.gopick.server.project.domain.ParticipationRequestStatus;
import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.dto.CreateParticipationRequest;
import com.gopick.server.project.dto.ParticipationRequestResponse;
import com.gopick.server.project.exception.InvalidProjectStateException;
import com.gopick.server.project.exception.ParticipationRequestConflictException;
import com.gopick.server.project.exception.ParticipationRequestNotFoundException;
import com.gopick.server.project.exception.ProjectAccessDeniedException;
import com.gopick.server.project.exception.ProjectValidationException;
import com.gopick.server.project.repository.ParticipationRequestRepository;
import com.gopick.server.project.repository.ProjectMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.gopick.server.project.domain.ParticipationRequestStatus.ACCEPTED;
import static com.gopick.server.project.domain.ParticipationRequestStatus.CANCELED;
import static com.gopick.server.project.domain.ParticipationRequestStatus.PENDING;
import static com.gopick.server.project.domain.ParticipationRequestType.APPLY;

@Service
public class ParticipationRequestService {
    private final ProjectAccess access;
    private final ParticipationRequestRepository requests;
    private final ProjectMemberRepository members;
    private final ProfileRepository profiles;
    private final NotificationService notifications;
    private final ProjectMembershipRepository memberships;

    public ParticipationRequestService(ProjectAccess access, ParticipationRequestRepository requests,
                                       ProjectMemberRepository members, ProfileRepository profiles, NotificationService notifications, ProjectMembershipRepository memberships) {
        this.access = access;
        this.requests = requests;
        this.members = members;
        this.profiles = profiles;
        this.notifications = notifications;
        this.memberships = memberships;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ParticipationRequestResponse create(Long accountId, String projectId, CreateParticipationRequest input) {
        Project project = access.lock(accountId, projectId);
        Profile sender;
        Profile participant;
        if (input.type() == APPLY) {
            if (input.targetProfileId() != null) {
                throw new ProjectValidationException("targetProfileId", "지원 요청은 지원자를 직접 지정할 수 없습니다.");
            }
            if (project.getStatus() != ProjectStatus.PREPARING || !project.getRecruiting()) {
                throw new InvalidProjectStateException("시작 전 공개 모집 중인 프로젝트에만 지원할 수 있습니다.");
            }
            sender = profiles.findByAccount_Id(accountId).orElseThrow(ProfileNotFoundException::new);
            participant = sender;
        } else {
            sender = access.requireLeader(project.getId(), accountId).getProfile();
            requireActiveProject(project);
            if (input.targetProfileId() == null) {
                throw new ProjectValidationException("targetProfileId", "제안 대상의 프로필 ID가 필요합니다.");
            }
            participant = profiles.findById(input.targetProfileId()).orElseThrow(ProfileNotFoundException::new);
        }

        requireNonMember(project, participant);
        if (requests.findFirstByProject_IdAndParticipant_IdAndStatus(
                project.getId(), participant.getId(), PENDING).isPresent()) {
            throw new ParticipationRequestConflictException("이 프로젝트에 응답 대기 중인 참여 요청이 있습니다.");
        }
        var saved = requests.save(new ParticipationRequest(project, sender, participant, input.type(), input.role()));
        var recipient = input.type() == APPLY ? currentLeader(project).getProfile().getAccount() : participant.getAccount();
        notifications.send(recipient, input.type() == APPLY ? NotificationType.APPLY_RECEIVED : NotificationType.OFFER_RECEIVED, project, saved.getId());
        return ParticipationRequestResponse.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ParticipationRequestResponse resolve(Long accountId, String projectId, String requestId,
                                                ParticipationRequestStatus status) {
        Project project = access.lock(accountId, projectId);
        Long id = access.parseId(requestId, "requestId");
        if (status == PENDING) {
            throw new ProjectValidationException("status", "ACCEPTED, REJECTED, CANCELED 중 하나여야 합니다.");
        }
        ParticipationRequest request = requests.findByIdAndProject_Id(id, project.getId())
                .orElseThrow(ParticipationRequestNotFoundException::new);
        requirePermission(accountId, request, status);
        if (request.getStatus() != PENDING) {
            throw new ParticipationRequestConflictException("이미 처리된 참여 요청입니다.");
        }
        if (status == ACCEPTED) {
            requireActiveProject(project);
            requireNonMember(project, request.getParticipant());
            members.save(new ProjectMember(project, request.getParticipant(), ProjectMemberType.MEMBER, request.getRole()));
        }
        request.resolve(status);
        if (status != CANCELED) {
            var type = request.getType() == APPLY
                    ? (status == ACCEPTED ? NotificationType.APPLY_ACCEPTED : NotificationType.APPLY_REJECTED)
                    : (status == ACCEPTED ? NotificationType.OFFER_ACCEPTED : NotificationType.OFFER_REJECTED);
            var recipient = request.getType() == APPLY ? request.getParticipant().getAccount() : request.getSender().getAccount();
            notifications.send(recipient, type, project, request.getId());
        }
        return ParticipationRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<ParticipationRequestResponse> sent(Long accountId) {
        access.requireAccount(accountId);
        return requests.findSent(accountId).stream().map(ParticipationRequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ParticipationRequestResponse> received(Long accountId) {
        access.requireAccount(accountId);
        return requests.findReceived(accountId).stream().map(ParticipationRequestResponse::from).toList();
    }

    private ProjectMember currentLeader(Project project) {
        return memberships.findMembers(project.getId()).stream().filter(m -> m.getMemberType() == ProjectMemberType.LEADER).findFirst().orElseThrow();
    }

    private void requirePermission(Long accountId, ParticipationRequest request, ParticipationRequestStatus status) {
        if (status == CANCELED) {
            requireProfileOwner(request.getSender(), accountId);
        } else if (request.getType() == APPLY) {
            access.requireLeader(request.getProject().getId(), accountId);
        } else {
            requireProfileOwner(request.getParticipant(), accountId);
        }
    }

    private void requireNonMember(Project project, Profile participant) {
        if (members.findByProject_IdAndProfile_Id(project.getId(), participant.getId()).isPresent()) {
            throw new ParticipationRequestConflictException("이미 프로젝트에 참여한 사용자입니다.");
        }
    }

    private void requireActiveProject(Project project) {
        if (project.getStatus() == ProjectStatus.COMPLETED) {
            throw new InvalidProjectStateException("완료된 프로젝트에는 새 팀원이 참여할 수 없습니다.");
        }
    }

    private void requireProfileOwner(Profile profile, Long accountId) {
        if (!profile.getAccount().getId().equals(accountId)) {
            throw new ProjectAccessDeniedException();
        }
    }
}
