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
    "createdAt": "2026-11-15T09:30:00",
    "read": false,
    "readAt": null
  }
]
```

`requestId`에는 참여 요청 관련 알림의 요청 ID가 담깁니다. 다른 이벤트에서는 `null`입니다. 읽음 처리 후에는 `read`가 `true`로 바뀌고 `readAt`에 최초 읽음 시각이 기록됩니다.

`projectId`와 `projectTitle`은 이벤트가 발생했을 때의 값입니다. 프로젝트가 삭제되어도 기존 알림은 유지됩니다. 프로젝트와 참여 요청을 조회할 때는 각 API의 접근 권한이 적용됩니다.

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

요청 취소, 팀원 하차, 프로젝트 삭제는 알림 생성 범위에서 제외됩니다. 알림은 해당 이벤트와 같은 트랜잭션으로 저장됩니다.
