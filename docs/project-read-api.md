# 프로젝트 목록·기본 상세 조회 API

`GET /projects`와 `GET /projects/{projectId}`는 로그인 세션이 필요합니다.

## 목록

```text
GET /projects?status=PREPARING&recruiting=true
GET /projects?status=IN_PROGRESS
GET /projects?status=COMPLETED
```

필터는 각각 생략할 수 있으며 함께 지정하면 AND 조건입니다.
`status`는 기존 `PREPARING`, `IN_PROGRESS`, `COMPLETED`, `recruiting`은 `true`/`false`입니다.
응답은 프로젝트 기본 정보 DTO의 JSON 배열이며 ID 내림차순입니다. 결과가 없으면 `[]`입니다.
생명주기는 응답에서 `lifecycleStatus`로 반환합니다.

## 상세

상세 응답은 `projectId`, `title`, `description`, `background`, `goal`, `wish`, `extra`,
`lifecycleStatus`, `stage`, `recruiting`, 생성/상태/단계 변경 시각과 `members`를 포함합니다.
`members`는 `profileId`, `name`, `isPublic`, `role`, `authority`, `joinedAt`을 반환합니다.
역할과 팀장/팀원 권한은 별개이며 계정 Entity와 연락처는 반환하지 않습니다.
다른 사용자의 비공개 프로필 이름은 `null`입니다.

상세 응답에는 프로젝트 속성 `attributes`, 경험 조건 `experience`, 역할별 모집 조건
`recruitments`, 관련 링크 `links`도 포함됩니다. 기존 프로젝트에 저장된 조건이 없으면
목록은 빈 배열이고 경험 조건의 `mode`는 `NONE`입니다.

## 오류

- `401 UNAUTHENTICATED`: 비로그인 또는 존재하지 않는 계정 세션
- `404 PROJECT_NOT_FOUND`: 존재하지 않는 프로젝트
- `400 VALIDATION_ERROR`: 잘못된 ID 또는 필터. `errors`에 필드별 메시지 포함

Java 21 JDK에서 `./gradlew test` 또는 Windows의 `.\gradlew.bat test`로 검증합니다.
