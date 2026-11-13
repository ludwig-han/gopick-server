# 프로젝트 완료 API

`POST /projects/{projectId}/complete`는 현재 프로젝트 팀장만 요청 본문 없이 호출할 수 있습니다.
`IN_PROGRESS` 프로젝트를 `COMPLETED`로 전환하고 `recruiting=false`를 저장합니다.
성공 시 `200 OK`와 현재 프로젝트의 기본 정보 DTO를 반환합니다.
완료 전 단계와 단계 변경 시각은 유지합니다.

| 오류 | 상황 |
|---|---|
| 400 VALIDATION_ERROR | 프로젝트 ID 형식 오류 |
| 401 UNAUTHENTICATED | 비로그인 또는 유효하지 않은 계정 세션 |
| 403 PROJECT_ACCESS_DENIED | MEMBER 또는 비구성원 |
| 404 PROJECT_NOT_FOUND | 존재하지 않는 프로젝트 |
| 409 INVALID_PROJECT_STATE | PREPARING 또는 이미 COMPLETED |
