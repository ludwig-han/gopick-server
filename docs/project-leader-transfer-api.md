# 프로젝트 팀장 권한 위임 API

`PATCH /projects/{projectId}/leader`는 진행 중인 프로젝트의 현재 팀장만 호출할 수 있습니다.
대상은 같은 프로젝트의 `MEMBER`이며 요청에는 대상의 프로필 ID를 보냅니다.

```json
{"targetProfileId": 21}
```

기존 팀장의 권한을 `MEMBER`로 바꾸고 대상의 권한을 `LEADER`로 바꾸는 작업은 하나의 트랜잭션에서 처리합니다.
두 사람의 프로젝트 역할은 유지합니다. 성공 시 `204 No Content`를 반환합니다.

| 오류 | 상황 |
|---|---|
| 400 VALIDATION_ERROR | 잘못된 ID 또는 위임 대상이 같은 프로젝트의 MEMBER가 아님 |
| 401 UNAUTHENTICATED | 비로그인 또는 유효하지 않은 계정 세션 |
| 403 PROJECT_ACCESS_DENIED | MEMBER 또는 비구성원 |
| 404 PROJECT_NOT_FOUND | 존재하지 않는 프로젝트 |
| 409 INVALID_PROJECT_STATE | 프로젝트가 IN_PROGRESS가 아님 |
