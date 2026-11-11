# 프로젝트 시작·진행 단계 API

## 시작

```text
POST /projects/{projectId}/start
```

Body 없이 해당 프로젝트의 LEADER가 호출합니다.
PREPARING을 IN_PROGRESS로 변경하는 동시에 `recruiting=false`로 바꿉니다.
기존 stage 값은 변경하지 않습니다. 이미 시작/완료된 프로젝트는 409입니다.

## 진행 단계

```text
PATCH /projects/{projectId}/stage
```

```json
{"stage": "IMPLEMENTATION"}
```

IN_PROGRESS 프로젝트의 현재 LEADER와 MEMBER가 호출할 수 있습니다.
기존 `ProjectStage`의 `PLANNING`, `DESIGN`, `IMPLEMENTATION`, `TESTING`, `PRODUCTION`을 사용합니다.
마지막 단계도 프로젝트를 자동 완료하지 않습니다.
PREPARING/COMPLETED의 단계 변경은 409입니다.

두 동작은 각각 하나의 트랜잭션과 프로젝트 행 잠금 안에서 권한·상태를 확인하고 변경합니다.
성공은 `200 OK`와 현재 기본 정보 DTO입니다. 생명주기는 응답에서 `lifecycleStatus`입니다.

| 오류 | 상황 |
|---|---|
| 400 VALIDATION_ERROR | 잘못된 ID 또는 stage |
| 401 UNAUTHENTICATED | 비로그인 또는 존재하지 않는 계정 세션 |
| 403 PROJECT_ACCESS_DENIED | 작업 권한이 없는 사용자 |
| 404 PROJECT_NOT_FOUND | 존재하지 않는 프로젝트 |
| 409 INVALID_PROJECT_STATE | 현재 생명주기에서 허용되지 않는 동작 |

Java 21 JDK에서 `./gradlew test` 또는 Windows의 `.\gradlew.bat test`로 검증합니다.
