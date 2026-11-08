# 프로필 상세 조회 API

이슈 #4의 `GET /profiles/{profileId}` 구현 계약입니다. 설계 기준은 첨부된
`GoPick_API_설계_v0.2_수정본.docx`입니다.

## 인증과 접근 권한

세션의 `accountId`로 현재 계정을 식별합니다. 아직 본인 프로필을 만들지 않은
계정도 로그인 상태라면 공개 프로필을 조회할 수 있습니다.

| 요청 | 응답 |
| --- | --- |
| 로그인한 사용자의 공개 프로필 조회 | `200 OK` |
| 본인의 비공개 프로필 조회 | `200 OK` |
| 다른 사용자의 비공개 프로필 조회 | `403 PRIVATE_PROFILE` |
| 존재하지 않는 프로필 조회 | `404 PROFILE_NOT_FOUND` |
| 비로그인 또는 유효하지 않은 계정 세션 | `401 UNAUTHENTICATED` |
| 숫자가 아니거나 1 미만 또는 Long 범위를 벗어난 ID | `400 VALIDATION_ERROR` |

세션이 없는 요청은 ID 형식과 관계없이 `401 UNAUTHENTICATED`를 반환합니다.
접근 권한을 먼저 검사하고, 권한이 있는 경우에만 연관 정보를 조회합니다.
본인 여부는 Profile ID와 계정 ID의 숫자를 비교하지 않고 `Profile.account.id`로 판단합니다.

## 응답 예시

```json
{
  "profileId": 12,
  "name": "홍길동",
  "major": "컴퓨터공학",
  "contactPlatform": "DISCORD",
  "contactId": "example",
  "isPublic": true,
  "teamTendency": "FOLLOW",
  "intro": "백엔드 개발에 관심이 있습니다.",
  "parts": ["BACKEND"],
  "technologies": [{"technology": "Java", "level": 3}],
  "interests": ["WEB"],
  "preferredRoles": ["BACKEND"],
  "preferences": [{"category": "COLLAB", "value": "정기 회의"}],
  "experiences": [
    {
      "experienceId": 5,
      "description": "팀 프로젝트 구현",
      "attributes": [{"category": "TECHNOLOGY", "value": "Java"}]
    }
  ]
}
```

v0.2에는 상세 조회의 성공 응답 스키마가 별도로 정의되어 있지 않습니다.
위 응답은 현재 프로필 데이터와 v0.2 프로필 생성 예시의 필드를 바탕으로 구성했습니다.
경험의 상세 구조는 현재 `ProfileExperience`와 `ExperienceAttribute`에 저장된 정보를 DTO로 매핑합니다.
연관 데이터가 없으면 해당 필드는 빈 배열입니다. 배열은 저장 ID 오름차순으로 반환합니다.
계정 엔티티, 계정 이메일, 비밀번호는 응답에 포함하지 않습니다.

## 오류 응답

```json
{
  "code": "PRIVATE_PROFILE",
  "message": "비공개 프로필은 본인만 조회할 수 있습니다."
}
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해주세요.",
  "errors": {"profileId": "profileId는 1 이상의 정수여야 합니다."}
}
```

오류 메시지 대신 `code`를 기준으로 프론트엔드에서 처리합니다.

## 테스트

JDK 21을 사용하여 실행합니다.

```powershell
.\gradlew.bat test
```

테스트는 테스트 전용 H2 DB를 사용합니다. 운영 MySQL 연결 설정과 스키마는 변경하지 않습니다.
로그인 세션, 공개/비공개 접근, 없는 ID, 잘못된 ID, 응답 필드, 빈 배열,
다른 프로필의 데이터가 섞이지 않는지 확인합니다.

이번 변경 범위는 이슈 #4의 프로필 상세 조회입니다. 목록/검색/추천/수정 및
프로젝트 생명주기 등 별도 기능은 이 변경에서 구현하지 않습니다.
