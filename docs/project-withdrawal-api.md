# 프로젝트 하차 API

`DELETE /projects/{projectId}/members/me`는 로그인 사용자가 본인의 프로젝트 참여를 종료합니다.
요청 본문은 필요 없으며 성공 시 `204 No Content`를 반환합니다.

| 프로젝트 상태 | 현재 권한 | 처리 |
|---|---|---|
| `PREPARING` | `MEMBER` | 요청자의 팀원 자격을 제거합니다. |
| `PREPARING` | `LEADER` | 프로젝트와 해당 프로젝트에 속한 데이터를 물리적으로 삭제합니다. |
| `IN_PROGRESS` | `MEMBER` | 요청자의 팀원 자격을 제거합니다. |
| `IN_PROGRESS` | `LEADER` | 다른 팀원에게 팀장 권한을 위임한 뒤 하차할 수 있습니다. |
| `COMPLETED` | `LEADER`, `MEMBER` | 참여 이력을 보존하며 하차 요청을 거부합니다. |

| 오류 | 상황 |
|---|---|
| `400 VALIDATION_ERROR` | `projectId`가 양의 정수가 아님 |
| `401 UNAUTHENTICATED` | 비로그인 또는 유효하지 않은 계정 세션 |
| `403 PROJECT_ACCESS_DENIED` | 요청자가 현재 팀원이 아님 |
| `404 PROJECT_NOT_FOUND` | 프로젝트가 존재하지 않음 |
| `409 INVALID_PROJECT_STATE` | 진행 중인 프로젝트의 팀장이 위임 전에 요청하거나 완료된 프로젝트에서 요청함 |
