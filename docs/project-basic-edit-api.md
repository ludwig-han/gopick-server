# 프로젝트 기본 정보 편집 API

`PATCH /projects/{projectId}`는 로그인한 해당 프로젝트 LEADER만 호출할 수 있습니다.

```json
{
  "title": "GoPick",
  "description": "팀 매칭 서비스",
  "background": "팀원 탐색",
  "goal": "서비스 구현",
  "wish": "백엔드 개발",
  "extra": "주 1회 회의",
  "recruiting": true
}
```

위 기본 정보와 함께 `attributes`, `experience`, `recruitments`, `links`를 보낼 수 있습니다.
예시는 [프로젝트 모집 정보](project-recruitment-api.md)를 참고하세요. 생략한 필드는 유지합니다.
명시적인 `null`은 거부하며 `title`은 공백일 수 없습니다. 텍스트 최대 길이는 기존 컬럼과 같은 255자입니다.
선택 텍스트를 지우려면 빈 문자열을 전달합니다. 지원하지 않는 필드는 검증 오류를 반환합니다.
일반 편집으로 stage, 생명주기 또는 팀원 권한은 변경하지 않습니다.

모든 변경은 하나의 트랜잭션에서 처리합니다. PREPARING에서만 `recruiting=true`로 설정할 수 있습니다.
성공 시 `200 OK`와 현재 프로젝트의 기본 정보 DTO를 반환합니다.
생명주기는 응답에서 `lifecycleStatus`입니다.

| 오류 | 상황 |
|---|---|
| 400 VALIDATION_ERROR | 입력·ID 오류, 지원하지 않는 필드 |
| 401 UNAUTHENTICATED | 비로그인 또는 존재하지 않는 계정 세션 |
| 403 PROJECT_ACCESS_DENIED | MEMBER 또는 비구성원 |
| 404 PROJECT_NOT_FOUND | 존재하지 않는 프로젝트 |
| 409 INVALID_PROJECT_STATE | 시작/완료된 프로젝트의 공개 모집 재개 |

Java 21 JDK에서 `./gradlew test` 또는 Windows의 `.\gradlew.bat test`로 검증합니다.
