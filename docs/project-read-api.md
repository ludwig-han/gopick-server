# 프로젝트 목록·기본 상세 조회 API

`GET /projects`, `GET /me/projects`, `GET /projects/{projectId}`는 모두 로그인 세션이 필요합니다.

## 목록

```text
GET /projects?status=PREPARING&recruiting=true
GET /projects?status=IN_PROGRESS
GET /projects?status=COMPLETED
```

필터는 각각 생략할 수 있으며 함께 지정하면 AND 조건입니다.
`status`는 기존 `PREPARING`, `IN_PROGRESS`, `COMPLETED`, `recruiting`은 `true`/`false`입니다.
`200 OK` 응답은 프로젝트 ID 내림차순의 JSON 배열입니다. 결과가 없으면 `[]`입니다.
각 항목은 프로젝트 기본 정보와 상세 조회와 같은 형식의 `members`, `recruitments`, `attributes`를 포함합니다.
데이터가 없는 배열은 `[]`입니다. 현재 팀원 수는 `members.length`로 확인합니다.

- `members`에는 `profileId`, `name`, `isPublic`, `role`, `authority`, `joinedAt`이 있습니다.
- `recruitments`에는 `role`, `remainingCount`, `techMode`, `technologies`가 있습니다. 각 기술은 `technology`와 `minLevel`을 포함합니다.
- `attributes`에는 `category`와 `value`가 있습니다. `category`는 `FIELD`, `PURPOSE`, `COLLAB` 중 하나입니다.

생명주기는 응답에서 `lifecycleStatus`로 반환합니다.

## 내 프로젝트 목록

`GET /me/projects`는 로그인한 사용자가 현재 팀원인 프로젝트만 ID 내림차순으로 반환합니다.
`200 OK` 응답의 각 항목은 `GET /projects`와 같은 형식에 본인의 팀 내 권한 `myAuthority`(`LEADER` 또는 `MEMBER`)가 추가됩니다.
프로필이 없거나 참여 중인 프로젝트가 없으면 `[]`를 반환합니다.

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
