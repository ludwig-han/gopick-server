# 알림 API

모든 API는 로그인 세션이 필요합니다.

## 내 알림 목록

`GET /me/notifications`는 최신 생성 순서로 본인 알림만 반환합니다. 각 항목에는 `id`, `type`, `projectId`, 이벤트 당시의 `projectTitle`, 참여 요청 알림의 `requestId` (그 외에는 null), `createdAt`, `read`, `readAt`이 있습니다. 프로젝트 ID와 제목은 스냅샷이므로 프로젝트 삭제 후에도 알림이 유지되며, 해당 프로젝트나 요청을 조회할 수 있다는 뜻은 아닙니다.

## 읽음 처리

`PATCH /me/notifications/{notificationId}/read`는 성공 시 204를 반환합니다. 이미 읽은 알림을 다시 처리해도 성공하며 최초 읽음 시각은 유지됩니다. 존재하지 않거나 다른 사람의 알림은 404를 반환합니다.

## 이벤트와 수신자

- `APPLY_RECEIVED`: 지원 시 현재 팀장
- `OFFER_RECEIVED`: 제안 대상자
- `APPLY_ACCEPTED`, `APPLY_REJECTED`: 지원자
- `OFFER_ACCEPTED`, `OFFER_REJECTED`: 최초 제안자 (이후 팀장이 변경되어도 동일)
- `LEADER_TRANSFERRED`: 새 팀장
- `PROJECT_STARTED`, `PROJECT_COMPLETED`, `PROJECT_STAGE_CHANGED`: 변경한 사람을 제외한 당시 팀원. 단계가 실제로 변경된 경우에만 알립니다.

요청 취소, 하차 및 삭제는 알림을 생성하지 않습니다. 알림은 해당 이벤트와 같은 트랜잭션으로 저장됩니다.
