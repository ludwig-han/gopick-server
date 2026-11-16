# 알림 API

두 요청 모두 로그인 세션이 필요합니다. 브라우저에서 호출할 때 `credentials: 'include'`를 사용합니다.

## 내 알림 목록

`GET /me/notifications`는 본인 알림을 ID 내림차순으로 정렬한 배열을 `200 OK`와 함께 반환합니다. 알림이 없으면 `[]`를 반환합니다.

```json
[
  {
    "id": 41,
    "type": "APPLY_RECEIVED",
    "projectId": 7,
    "projectTitle": "GoPick",
    "requestId": 12,
    "requestAvailable": true,
    "actorName": "김수뭉",
    "createdAt": "2026-11-15T09:30:00",
    "read": false,
    "readAt": null
  }
]
```

`requestId`에는 참여 요청 관련 알림의 요청 ID가 담깁니다. 다른 이벤트에서는 `null`입니다. 읽음 처리 후에는 `read`가 `true`로 바뀌고 `readAt`에 최초 읽음 시각이 기록됩니다.

`actorName`은 이벤트를 일으킨 사람의 현재 공개 프로필 이름입니다. 비공개이거나 회원 탈퇴·프로필 삭제로 확인할 수 없는 경우와 기존 알림은 `null`입니다. 이름은 조회 시점에 결정되므로 변경될 수 있습니다. 연락처는 포함하지 않습니다.

`requestAvailable`은 참여 요청 알림에서 현재 로그인한 사용자의 보낸 요청 또는 받은 요청 목록에 해당 요청이 표시되는지를 나타냅니다. 요청이나 프로젝트가 삭제됐거나 팀장 변경 등으로 목록에서 확인할 수 없으면 `false`입니다. 참여 요청과 관계없는 알림은 `null`입니다. 이 값은 조회 시점의 상태이며 요청을 승인·거절할 수 있다는 뜻은 아닙니다.

프론트는 `requestAvailable`이 `true`일 때 `APPLY_RECEIVED`, `OFFER_RECEIVED`는 받은 요청 목록(`GET /me/participation-requests/received`)으로, `APPLY_ACCEPTED`, `APPLY_REJECTED`, `OFFER_ACCEPTED`, `OFFER_REJECTED`는 보낸 요청 목록(`GET /me/participation-requests/sent`)으로 이동해 `requestId`로 항목을 찾을 수 있습니다. 현재 요청 단건 조회 API는 없습니다. `false`이거나 목록 조회 사이에 요청이 사라져 항목을 찾지 못하면 “확인할 수 없는 요청입니다”로 안내합니다. 알림은 유지합니다.

`projectId`와 `projectTitle`은 이벤트가 발생했을 때의 값입니다. 프로젝트가 삭제되어도 기존 알림은 유지됩니다. 프로젝트와 참여 요청을 조회할 때는 각 API의 접근 권한이 적용됩니다.

회원 탈퇴 시 해당 계정이 받은 알림은 삭제됩니다. 다른 사용자의 알림은 유지되므로, 탈퇴로 삭제된 참여 요청의 `requestId`가 남아 해당 요청을 더 이상 조회할 수 없을 수 있습니다.

## 읽음 처리

`PATCH /me/notifications/{notificationId}/read`에 알림의 `id`를 양의 정수로 전달합니다. 요청 본문 없이 호출하며 성공 시 `204 No Content`를 반환합니다. 이미 읽은 알림에 다시 요청해도 `204`를 반환하고 최초 읽음 시각을 유지합니다.

| 상황 | 응답 |
|---|---|
| 비로그인 또는 유효하지 않은 계정 세션 | `401 UNAUTHENTICATED` |
| 잘못된 `notificationId` | `400 VALIDATION_ERROR` (`errors.notificationId`에 메시지 포함) |
| 존재하지 않거나 다른 사용자의 알림 | `404 NOTIFICATION_NOT_FOUND` |

## 이벤트와 수신자

| `type` | 수신자 |
|---|---|
| `APPLY_RECEIVED` | 지원 당시 팀장 |
| `OFFER_RECEIVED` | 제안 대상자 |
| `APPLY_ACCEPTED`, `APPLY_REJECTED` | 지원자 |
| `OFFER_ACCEPTED`, `OFFER_REJECTED` | 최초 제안자. 이후 팀장이 변경되어도 동일합니다. |
| `LEADER_TRANSFERRED` | 새 팀장 |
| `PROJECT_STARTED`, `PROJECT_COMPLETED` | 상태를 변경한 사람을 제외한 당시 팀원 |
| `PROJECT_STAGE_CHANGED` | 진행 단계가 실제로 바뀔 때 변경한 사람을 제외한 당시 팀원 |
| `MEMBER_LEFT` | 준비 중 또는 진행 중 프로젝트에서 팀원이 하차할 때 현재 팀장 |
| `PROJECT_DELETED` | 팀장의 프로젝트 삭제, 준비 중 팀장 하차 또는 회원 탈퇴로 프로젝트가 삭제될 때 해당 팀장을 제외한 당시 팀원 |

요청 취소는 알림 생성 범위에서 제외됩니다. 일반 팀원의 회원 탈퇴는 프로젝트 하차 API 호출과 별개이며 `MEMBER_LEFT`를 생성하지 않습니다. 알림은 해당 이벤트와 같은 트랜잭션으로 저장됩니다. 삭제 알림의 프로젝트는 더 이상 조회할 수 없습니다.

## MySQL 배포

MySQL에 `notification` 테이블이 이미 있고 이 변경을 적용한 적이 없다면, 배포 전에 DB를 백업한 뒤 [마이그레이션 SQL](migrations/notification-actor-and-event-types.sql)을 한 번 실행합니다. 새 DB나 `notification` 테이블이 없는 DB에서는 앱 시작 시 Hibernate가 최신 테이블을 생성합니다.

여러 인스턴스가 같은 DB를 사용한다면 새 알림 유형을 기록하기 전에 이전 버전 인스턴스를 중지하거나 교체합니다. 이전 버전에서 `MEMBER_LEFT` 또는 `PROJECT_DELETED` 알림을 읽으면 오류가 발생합니다. 기존 알림의 `actorName`은 `null`입니다.
