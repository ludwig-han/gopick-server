# 프로젝트 참여 요청 API

모든 API는 로그인 세션이 필요합니다. 브라우저 요청에는 `credentials: 'include'`를 설정합니다.

## 요청 생성

`POST /projects/{projectId}/participation-requests`는 `201 Created`와 `PENDING` 요청을 반환합니다.

| 유형 | 요청 본문 예시 | 조건 |
|---|---|---|
| `APPLY` | `{"type":"APPLY","role":"BACKEND"}` | 본인의 프로필이 필요합니다. 프로젝트는 `PREPARING`이며 `recruiting=true`여야 합니다. |
| `OFFER` | `{"type":"OFFER","role":"DESIGN","targetProfileId":42}` | `PREPARING` 또는 `IN_PROGRESS` 프로젝트의 현재 팀장만 특정 프로필에 제안할 수 있습니다. 공개 모집 여부에는 제한이 없습니다. |

프로젝트에 이미 참여 중인 사용자나 같은 프로젝트에서 응답 대기 중인 사용자에 대한 요청은 거부됩니다. `APPLY`와 `OFFER` 사이에도 동일한 중복 규칙을 적용합니다. 거절하거나 취소한 뒤에는 생성 조건을 충족하는 경우 새 요청을 보낼 수 있습니다.

## 요청 처리

`PATCH /projects/{projectId}/participation-requests/{requestId}`에 `{"status":"ACCEPTED"}`처럼 상태를 보냅니다. 성공 시 `200 OK`와 처리된 요청을 반환합니다.

| 유형 | `ACCEPTED` 또는 `REJECTED` | `CANCELED` |
|---|---|---|
| `APPLY` | 프로젝트의 현재 팀장 | 지원한 사용자 |
| `OFFER` | 제안을 받은 사용자 | 제안을 보낸 사용자 |

`PENDING` 요청만 처리할 수 있습니다. `ACCEPTED` 처리 시 요청에 적힌 `role`로 `MEMBER`가 함께 등록됩니다. 이미 받은 `APPLY`는 공개 모집을 닫거나 프로젝트를 시작한 뒤에도 현재 팀장이 승인할 수 있습니다. 프로젝트가 완료된 뒤에는 남은 요청을 거절하거나 취소할 수 있습니다.

## 보낸 요청과 받은 요청

- `GET /me/participation-requests/sent`는 본인이 보낸 `APPLY`와 `OFFER`를 조회합니다.
- `GET /me/participation-requests/received`는 본인이 팀장인 프로젝트의 `APPLY`와 본인에게 온 `OFFER`를 조회합니다.

목록은 상태와 관계없이 요청 ID 내림차순으로 반환하며 결과가 없으면 `[]`입니다. 생성, 처리, 목록의 항목 형식은 같습니다.

```json
{
  "requestId": 11,
  "projectId": 1,
  "projectTitle": "GoPick",
  "type": "APPLY",
  "role": "BACKEND",
  "status": "PENDING",
  "senderProfileId": 42,
  "participantProfileId": 42,
  "createdAt": "2026-11-15T12:00:00",
  "resolvedAt": null
}
```

`participantProfileId`는 승인될 경우 프로젝트에 참여할 프로필입니다. `APPLY`에서는 지원자, `OFFER`에서는 제안 대상입니다. 계정·연락처 정보는 응답에 포함하지 않습니다.

| 오류 | 상황 |
|---|---|
| `400 VALIDATION_ERROR` | 잘못된 ID, 입력값 또는 상태 |
| `401 UNAUTHENTICATED` | 비로그인 또는 유효하지 않은 세션 |
| `403 PROJECT_ACCESS_DENIED` | 해당 동작에 필요한 권한이 없음 |
| `404 PROJECT_NOT_FOUND`, `PROFILE_NOT_FOUND`, `PARTICIPATION_REQUEST_NOT_FOUND` | 프로젝트, 프로필 또는 해당 프로젝트의 요청이 없음 |
| `409 INVALID_PROJECT_STATE` | 프로젝트 상태가 생성 또는 수락 조건을 충족하지 않음 |
| `409 PARTICIPATION_REQUEST_CONFLICT` | 중복 요청·참여 또는 이미 처리된 요청 |
