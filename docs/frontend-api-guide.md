# GoPick 프론트 API 연동 가이드

프론트엔드에서 GoPick 백엔드 API를 연결할 때 필요한 내용만 정리한 문서입니다.

> **기준**
>
> - 백엔드: `gopick-server` 최신 `main`
> - 프론트엔드: `gopick-web` 최신 `main`
> - 실제 구현된 API만 작성합니다.
> - staging 서버는 수동 배포이므로 최신 `main` 반영 여부는 백엔드에 확인해주세요.

---

## 1. 먼저 이것만 확인하세요

### Base URL

Staging:

```text
https://3-39-65-219.nip.io
```

로컬 백엔드:

```text
http://localhost:8080
```

### 인증

GoPick은 현재 **세션 기반 인증**을 사용합니다.

회원가입 또는 로그인에 성공하면 서버가 `JSESSIONID` 쿠키를 발급합니다.  
프론트에서 로그인 이후 API를 호출할 때는 반드시 쿠키를 함께 보내야 합니다.

```ts
fetch(`${BASE_URL}/me`, {
  credentials: 'include',
});
```

JSON 요청도 동일합니다.

```ts
fetch(`${BASE_URL}/profiles/${profileId}`, {
  method: 'PATCH',
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify(request),
});
```

> `credentials: 'include'`가 빠지면 서버에서는 비로그인 요청으로 처리됩니다.

현재 로컬 프론트에서 허용된 Origin:

```text
http://localhost:5173
http://127.0.0.1:5173
```

---

## 2. 현재 사용 가능한 API

| 기능 | Method | Endpoint |
|---|---|---|
| 회원가입 | `POST` | `/accounts` |
| 로그인 | `POST` | `/auth/login` |
| 로그아웃 | `POST` | `/auth/logout` |
| 현재 사용자 조회 | `GET` | `/me` |
| 프로필 생성 | `POST` | `/profiles` |
| 프로필 상세 조회 | `GET` | `/profiles/{profileId}` |
| 프로필 수정 | `PATCH` | `/profiles/{profileId}` |
| 프로젝트 목록 조회 | `GET` | `/projects` |
| 프로젝트 상세 조회 | `GET` | `/projects/{projectId}` |
| 프로젝트 기본 정보 수정 | `PATCH` | `/projects/{projectId}` |
| 공개 프로필 검색 | `GET` | `/profiles` |
| 프로젝트 생성 | `POST` | `/projects` |
| 프로젝트 시작 | `POST` | `/projects/{projectId}/start` |
| 프로젝트 단계 변경 | `PATCH` | `/projects/{projectId}/stage` |
| 프로젝트 완료 | `POST` | `/projects/{projectId}/complete` |
| 프로젝트 팀장 위임 | `PATCH` | `/projects/{projectId}/leader` |
| 프로필 북마크 추가 / 취소 | `POST` / `DELETE` | `/profiles/{targetProfileId}/bookmarks` |
| 내 북마크 목록 | `GET` | `/me/bookmarked-profiles` |
| 프로젝트 스크랩 추가 / 취소 | `POST` / `DELETE` | `/projects/{projectId}/scraps` |
| 내 스크랩 목록 | `GET` | `/me/scrapped-projects` |

---

## 3. 계정 API

### 회원가입

```text
POST /accounts
```

비밀번호 규칙:

- 8자 이상
- 영문 1자 이상 포함
- 숫자 1자 이상 포함

Request:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

성공:

```text
201 Created
```

```json
{
  "accountId": 1,
  "email": "user@example.com",
  "profileId": null
}
```

회원가입 성공 시 로그인 세션도 함께 생성됩니다.

주요 실패:

| 상황 | 응답 |
|---|---|
| 입력값 오류 | `400 VALIDATION_ERROR` |
| 중복 이메일 | `409 DUPLICATE_EMAIL` |

---

### 로그인

```text
POST /auth/login
```

Request:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

성공:

```text
200 OK
```

```json
{
  "accountId": 1,
  "email": "user@example.com",
  "profileId": 3
}
```

프로필이 없다면 `profileId`는 `null`입니다.

로그인 실패:

```text
401 INVALID_CREDENTIALS
```

이메일이 없거나 비밀번호가 틀린 경우 모두 같은 에러를 반환합니다.

---

### 현재 사용자 조회

```text
GET /me
```

성공:

```json
{
  "accountId": 1,
  "email": "user@example.com",
  "profileId": 3
}
```

비로그인:

```text
401 UNAUTHENTICATED
```

---

### 로그아웃

```text
POST /auth/logout
```

성공:

```text
204 No Content
```

세션이 이미 없어도 `204`를 반환합니다.

---

## 4. 프로필 생성 / 수정

프로필 생성과 수정은 **같은 Request Body**를 사용합니다.

생성:

```text
POST /profiles
```

수정:

```text
PATCH /profiles/{profileId}
```

둘 다 로그인 세션이 필요합니다.

### Request 예시

```json
{
  "name": "김수뭉",
  "major": "컴퓨터과학과",
  "contactPlatform": "디스코드",
  "contactId": "sumung.k",
  "isPublic": true,
  "parts": ["Dev"],
  "technologies": [
    {
      "technology": "Java",
      "level": 3
    },
    {
      "technology": "Spring Boot",
      "level": 2
    }
  ],
  "interests": ["웹", "AI"],
  "preferredRoles": ["Backend"],
  "teamTendency": "Follow",
  "preferences": {
    "collaboration": ["빠른 피드백"],
    "styles": ["일정 준수"],
    "strengths": ["문제 해결"],
    "projectTendencies": ["실사용 서비스 선호"],
    "communication": ["피드백 환영"]
  },
  "experiences": [
    {
      "types": ["팀 프로젝트"],
      "fields": ["웹"],
      "roles": ["Backend"],
      "technologies": ["Java", "Spring Boot"],
      "description": "GoPick 백엔드 개발"
    }
  ],
  "intro": "Spring Boot 기반 백엔드 개발에 관심이 있습니다."
}
```

### 필수값

| 필드                | 조건 |
|-------------------|---|
| `name`            | 빈 문자열 불가 |
| `major`           | 빈 문자열 불가 |
| `contactPlatform` | 빈 문자열 불가 |
| `contactId`       | 빈 문자열 불가 |
| `isPublic`        | 필수 |
| `parts`           | 최소 1개 |
| `technologies`    | 최소 1개 |
| `interests`       | 최소 1개 |
| `preferredRoles`  | 최소 1개 |

`teamTendency`, `intro`, `preferences`, `experiences`는 선택값입니다.

### 기술 수준

`technologies[].level`은 `1~4`입니다.

| 프론트 표시 | API 값 |
|---|---:|
| 입문 | 1 |
| 기초 경험 | 2 |
| 실전 경험 | 3 |
| 심화 경험 | 4 |

### 생성 성공

```text
201 Created
```

```json
{
  "profileId": 3
}
```

한 계정에는 하나의 프로필만 만들 수 있습니다.

이미 프로필이 있는 경우:

```text
409 DUPLICATE_PROFILE
```

### 수정 성공

```text
204 No Content
```

응답 Body는 없습니다.

본인 프로필만 수정할 수 있습니다.

| 상황 | 응답 |
|---|---|
| 입력값 오류 | `400 VALIDATION_ERROR` |
| 비로그인 | `401 UNAUTHENTICATED` |
| 다른 사용자의 프로필 수정 | `403 PROFILE_ACCESS_DENIED` |
| 없는 프로필 | `404 PROFILE_NOT_FOUND` |

---

## 5. 프로필 상세 조회

```text
GET /profiles/{profileId}
```

로그인 세션이 필요합니다.

- 공개 프로필: 조회 가능
- 본인의 비공개 프로필: 조회 가능
- 다른 사용자의 비공개 프로필: 조회 불가

본인 프로필 조회 Response 예시:

```json
{
  "profileId": 3,
  "name": "김수뭉",
  "major": "컴퓨터과학과",
  "contactPlatform": "디스코드",
  "contactId": "sumung.k",
  "isPublic": true,
  "teamTendency": "Follow",
  "intro": "Spring Boot 기반 백엔드 개발에 관심이 있습니다.",
  "parts": ["Dev"],
  "technologies": [
    {
      "technology": "Java",
      "level": 3
    }
  ],
  "interests": ["웹"],
  "preferredRoles": ["Backend"],
  "preferences": [
    {
      "category": "COLLAB",
      "value": "빠른 피드백"
    }
  ],
  "experiences": [
    {
      "experienceId": 10,
      "description": "GoPick 백엔드 개발",
      "attributes": [
        {
          "category": "TYPE",
          "value": "팀 프로젝트"
        },
        {
          "category": "TECHNOLOGY",
          "value": "Java"
        }
      ]
    }
  ]
}
```

주요 실패:

| 상황 | 응답 |
|---|---|
| 잘못된 `profileId` | `400 VALIDATION_ERROR` |
| 비로그인 | `401 UNAUTHENTICATED` |
| 다른 사용자의 비공개 프로필 | `403 PRIVATE_PROFILE` |
| 없는 프로필 | `404 PROFILE_NOT_FOUND` |


### Category 값

`preferences[].category`

| category | 생성/수정 Request 필드 |
|---|---|
| `COLLAB` | `preferences.collaboration` |
| `STYLE` | `preferences.styles` |
| `STRENGTH` | `preferences.strengths` |
| `PROJECT` | `preferences.projectTendencies` |
| `COMM` | `preferences.communication` |

`experiences[].attributes[].category`

| category | 생성/수정 Request 필드 |
|---|---|
| `TYPE` | `experiences[].types` |
| `FIELD` | `experiences[].fields` |
| `ROLE` | `experiences[].roles` |
| `TECHNOLOGY` | `experiences[].technologies` |

### 연락처

`contactPlatform`과 `contactId`는 요청한 사용자가 해당 프로필의 본인인지에 따라 다르게 반환됩니다.

- 본인 프로필 조회: `contactPlatform`, `contactId` 포함
- 다른 사용자의 공개 프로필 조회: `contactPlatform`, `contactId`를 응답에 포함하지 않음

실제 연락처가 다른 사용자에게 노출되지 않도록 하기 위한 정책입니다.

---

## 6. 현재 프론트 `ProfileForm`과 API 차이

현재 `gopick-web`의 실제 `ProfileForm`과 백엔드 API는 일부 이름과 구조가 다릅니다.

### 기본 필드

| 프론트 | API               |
|---|-------------------|
| `name` | `name`            |
| `major` | `major`           |
| `contactType` | `contactPlatform` |
| `contactValue` | `contactId`       |
| `isPublic` | `isPublic`        |
| `parts` | `parts`           |
| `fields` | `interests`       |
| `wantedRoles` | `preferredRoles`  |
| `teamTendency` | `teamTendency`    |
| `intro` | `intro`           |

### 기술 스택

프론트:

```ts
stacks: ['Java', 'React'];

levels: {
  Java: '실전 경험',
  React: '기초 경험',
};
```

API:

```json
{
  "technologies": [
    {
      "technology": "Java",
      "level": 3
    },
    {
      "technology": "React",
      "level": 2
    }
  ]
}
```

### 협업 성향

| 프론트 | API |
|---|---|
| `collab.preferences` | `preferences.collaboration` |
| `collab.styles` | `preferences.styles` |
| `collab.strengths` | `preferences.strengths` |
| `collab.tendencies` | `preferences.projectTendencies` |
| `collab.communication` | `preferences.communication` |

### 프로젝트 경험

| 프론트 | API |
|---|---|
| `experiences[].types` | `experiences[].types` |
| `experiences[].fields` | `experiences[].fields` |
| `experiences[].roles` | `experiences[].roles` |
| `experiences[].stacks` | `experiences[].technologies` |
| `experiences[].detail` | `experiences[].description` |

---

## 7. 연동 전에 아직 결정할 부분

현재 코드상 아래 부분은 프론트와 백엔드 표현이 다르며, 처리 위치는 아직 결정하지 않았습니다.

1. `ProfileForm`과 API Request 사이의 변수명/구조 변환
2. `입문 / 기초 경험 / 실전 경험 / 심화 경험`과 API `1~4` 변환
3. 프론트의 `기타` + 직접 입력값 처리
    - `stackEtc`
    - `fieldEtc`
    - `roleEtc`
    - 경험의 `typeEtc`, `fieldEtc`, `roleEtc`, `stackEtc`
4. 상세조회 Response를 현재 `ProfileForm`으로 불러오는 방식
5. 내 프로필 수정 화면에서 기존 `contactValue`를 가져오는 방식

이 부분은 아직 한쪽에서 임의로 구현하지 말고 프론트/백엔드 간 요청 형식을 확정한 뒤 연결합니다.

---

## 8. 공통 에러 형식

일반 에러:

```json
{
  "code": "UNAUTHENTICATED",
  "message": "로그인이 필요합니다."
}
```

Validation 에러:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해주세요.",
  "errors": {
    "name": "must not be blank"
  }
}
```

프론트에서 에러 처리가 필요한 경우 가능하면 `message` 문구 자체보다 `code`를 기준으로 분기해주세요.


---

## 9. 프로젝트 목록 / 상세 조회 / 기본 정보 수정

세 API 모두 로그인 세션이 필요하므로 `credentials: 'include'`를 설정합니다.

### 프로젝트 목록 조회

```text
GET /projects?status=PREPARING&recruiting=true
```

| 쿼리 파라미터 | 값 | 설명 |
|---|---|---|
| `status` | `PREPARING`, `IN_PROGRESS`, `COMPLETED` | 선택. 프로젝트 생명주기 상태 |
| `recruiting` | `true`, `false` | 선택. 공개 모집 여부 |

두 필터 모두 생략 가능하며, 함께 지정하면 AND 조건으로 적용됩니다. 결과는 프로젝트 ID 내림차순이며 없으면 빈 배열 `[]`을 반환합니다.

성공: `200 OK`

```json
[
  {
    "projectId": 1,
    "title": "GoPick",
    "description": "팀 매칭 서비스",
    "background": "팀원 탐색",
    "goal": "서비스 구현",
    "wish": "백엔드 개발",
    "extra": "주 1회 회의",
    "lifecycleStatus": "PREPARING",
    "stage": null,
    "recruiting": true,
    "createdAt": "2026-11-11T12:00:00",
    "statusUpdatedAt": "2026-11-11T12:00:00",
    "stageUpdatedAt": null
  }
]
```

응답의 생명주기 필드명은 `status`가 아닌 `lifecycleStatus`입니다. 단계 `stage`는 설정 전 `null`일 수 있으며, 설정된 값은 `PLANNING`, `DESIGN`, `IMPLEMENTATION`, `TESTING`, `PRODUCTION` 중 하나입니다.

### 프로젝트 상세 조회

```text
GET /projects/{projectId}
```

성공: `200 OK`. 아래는 기본 정보와 팀원만 표시한 예시입니다. 실제 응답에는 `attributes`, `experience`, `recruitments`, `links`도 포함되며, 모집 정보는 10절을 참고하세요.

```json
{
  "projectId": 1,
  "title": "GoPick",
  "description": "팀 매칭 서비스",
  "background": "팀원 탐색",
  "goal": "서비스 구현",
  "wish": "백엔드 개발",
  "extra": "주 1회 회의",
  "lifecycleStatus": "PREPARING",
  "stage": null,
  "recruiting": true,
  "createdAt": "2026-11-11T12:00:00",
  "statusUpdatedAt": "2026-11-11T12:00:00",
  "stageUpdatedAt": null,
  "members": [
    {
      "profileId": 3,
      "name": "김수뭉",
      "isPublic": true,
      "role": "BACKEND",
      "authority": "LEADER",
      "joinedAt": "2026-11-11T12:00:00"
    }
  ]
}
```

`role`은 `PM`, `FRONTEND`, `BACKEND`, `AI`, `DESIGN`, `OTHERS` 중 하나이며, `authority`는 `LEADER` 또는 `MEMBER`입니다. 다른 사용자의 비공개 프로필은 `name`이 `null`로 반환됩니다. 계정 정보와 연락처는 반환하지 않습니다.

### 프로젝트 기본 정보 수정

```text
PATCH /projects/{projectId}
```

해당 프로젝트의 `LEADER`만 수정할 수 있습니다.

Request 예시:

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

- 수정할 필드만 보내며 생략한 필드는 유지됩니다. 명시적인 `null`과 지원하지 않는 필드는 허용하지 않습니다.
- 텍스트 필드는 최대 255자입니다. `title`은 빈 문자열이나 공백만 보낼 수 없습니다. 나머지 텍스트를 지우려면 빈 문자열을 보냅니다.
- `recruiting: true`는 `PREPARING` 상태에서만 가능합니다.
- 이 API로 생명주기, 단계, 팀원 권한을 변경할 수 없습니다.

성공: `200 OK`와 수정 후 프로젝트 기본 정보 객체를 반환합니다. 응답 구조는 목록 배열의 각 항목과 같고 `members`는 포함하지 않습니다.

### 주요 오류

| 상황 | 응답 |
|---|---|
| 잘못된 프로젝트 ID, 필터 또는 수정 요청 | `400 VALIDATION_ERROR` |
| 비로그인 또는 존재하지 않는 계정의 세션 | `401 UNAUTHENTICATED` |
| 팀원 또는 비구성원이 수정 요청 | `403 PROJECT_ACCESS_DENIED` |
| 없는 프로젝트 상세 조회 또는 수정 | `404 PROJECT_NOT_FOUND` |
| 시작·완료된 프로젝트에서 공개 모집 재개 | `409 INVALID_PROJECT_STATE` |


---

## 10. 프로젝트 생성 및 모집 정보

### 프로젝트 생성

`POST /projects`는 로그인 세션과 생성자의 프로필이 필요합니다. 생성자는 요청한 `role`을 가진 `LEADER`가 됩니다. `title`, `role`, `recruiting`은 필수입니다. `role`은 `PM`, `FRONTEND`, `BACKEND`, `AI`, `DESIGN`, `OTHERS` 중 하나입니다. 기본 설명 필드와 모집 정보는 선택입니다.

요청 예시:

```json
{
  "title": "GoPick",
  "description": "팀 매칭 서비스",
  "background": "팀원 탐색",
  "goal": "서비스 구현",
  "wish": "백엔드 개발",
  "extra": "주 1회 회의",
  "role": "PM",
  "recruiting": true,
  "attributes": [
    {"category": "FIELD", "value": "WEB"},
    {"category": "PURPOSE", "value": "포트폴리오"},
    {"category": "COLLAB", "value": "코드 리뷰 선호"}
  ],
  "experience": {"mode": "PREFERRED", "types": ["HACKATHON"], "fields": ["WEB"]},
  "recruitments": [
    {"role": "BACKEND", "remainingCount": 2, "techMode": "REQUIRED", "technologies": [{"technology": "Java", "minLevel": 3}]}
  ],
  "links": [{"type": "GitHub", "url": "https://github.com/example/gopick"}]
}
```

성공하면 `201 Created`와 `{"projectId": 1}`을 반환합니다. 프로필이 없으면 `404 PROFILE_NOT_FOUND`, 입력값이 잘못되면 `400 VALIDATION_ERROR`입니다.

### 모집 정보 수정과 상세 조회

팀장은 `PATCH /projects/{projectId}`에서 9절의 기본 정보뿐 아니라 `attributes`, `experience`, `recruitments`, `links`도 수정할 수 있습니다. 요청에 포함한 목록은 전체 교체되며 빈 배열은 목록을 비웁니다. 생략한 필드는 유지하고 명시적 `null`은 허용하지 않습니다. 경험 조건은 `{"mode":"NONE"}`으로 해제합니다.

- `attributes`: 프로젝트 속성의 `category`, `value` 목록입니다. `category`는 `FIELD`, `PURPOSE`, `COLLAB` 중 하나입니다.
- `experience`: 경험 조건 `mode`, `types`, `fields`입니다.
- `recruitments`: 역할별 모집 조건입니다. `role`은 중복할 수 없고, `remainingCount`는 0 이상입니다. 기술별 `minLevel`은 1~4입니다.
- 기술 조건 `techMode`와 경험 조건 `mode`는 `NONE`, `PREFERRED`, `REQUIRED`를 사용합니다. 기술 조건이 `NONE`이면 기술 목록을 비우고, `PREFERRED` 또는 `REQUIRED`이면 하나 이상 입력합니다.
- `links`: `type`과 `url` 목록입니다. URL은 호스트가 있는 HTTP 또는 HTTPS 주소여야 합니다.

수정 성공 시 `200 OK`와 프로젝트 기본 정보만 반환하므로 저장된 모집 정보는 `GET /projects/{projectId}`로 확인합니다. 상세 응답에 모집 정보가 없으면 목록은 `[]`, 경험 조건은 `mode: "NONE"`입니다. 입력 오류가 있으면 전체 변경은 반영되지 않습니다. 자세한 계약은 [프로젝트 모집 정보](https://github.com/ludwig-han/gopick-server/blob/main/docs/project-recruitment-api.md)를 참고하세요.

---

## 11. 프로젝트 시작, 단계 변경, 완료와 팀장 위임

모든 요청은 로그인 세션이 필요합니다.

| 동작 | 요청 | 호출 권한과 상태 | 성공 |
|---|---|---|---|
| 시작 | `POST /projects/{projectId}/start` | `PREPARING` 프로젝트의 `LEADER` | `200 OK`, 기본 정보 |
| 단계 변경 | `PATCH /projects/{projectId}/stage` | `IN_PROGRESS` 프로젝트의 `LEADER` 또는 `MEMBER` | `200 OK`, 기본 정보 |
| 완료 | `POST /projects/{projectId}/complete` | `IN_PROGRESS` 프로젝트의 `LEADER` | `200 OK`, 기본 정보 |
| 팀장 위임 | `PATCH /projects/{projectId}/leader` | `IN_PROGRESS` 프로젝트의 `LEADER` | `204 No Content` |

시작과 완료는 요청 본문이 없습니다. 시작은 상태를 `IN_PROGRESS`로, 완료는 `COMPLETED`로 변경하며 둘 다 `recruiting=false`로 저장합니다. 기존 `stage`는 유지합니다.

단계 변경 요청:

```json
{"stage": "IMPLEMENTATION"}
```

`stage`는 `PLANNING`, `DESIGN`, `IMPLEMENTATION`, `TESTING`, `PRODUCTION` 중 하나입니다. 마지막 단계로 바꿔도 자동으로 완료되지 않습니다.

팀장 위임 요청:

```json
{"targetProfileId": 21}
```

대상은 같은 프로젝트의 `MEMBER`여야 합니다. 기존 팀장은 `MEMBER`가 되고 대상은 `LEADER`가 되며 각자의 `role`은 유지됩니다. 상세 조회의 `members[].authority`로 확인할 수 있습니다.

| 상황 | 응답 |
|---|---|
| 잘못된 ID, 단계 또는 위임 대상 | `400 VALIDATION_ERROR` |
| 비로그인 또는 유효하지 않은 계정 세션 | `401 UNAUTHENTICATED` |
| 동작 권한이 없는 사용자 | `403 PROJECT_ACCESS_DENIED` |
| 없는 프로젝트 | `404 PROJECT_NOT_FOUND` |
| 현재 상태에서 허용되지 않는 동작 | `409 INVALID_PROJECT_STATE` |

---

## 12. 공개 프로필 검색

```text
GET /profiles?technology=Java&interest=WEB&preferredRole=BACKEND
```

로그인 사용자는 프로필 생성 여부와 관계없이 공개 프로필을 검색할 수 있습니다. `technology`, `interest`, `preferredRole`은 모두 선택이며 여러 조건은 AND로 적용합니다. 빈 값은 생략한 것으로 처리하고 앞뒤 공백과 대소문자를 무시하여 일치하는 값을 검색합니다.

성공: `200 OK`. 프로필 ID 내림차순 배열이며 결과가 없으면 `[]`입니다. 연락처 플랫폼과 ID는 반환하지 않습니다.

```json
[
  {
    "profileId": 12,
    "name": "홍길동",
    "major": "컴퓨터공학",
    "intro": "백엔드 개발에 관심이 있습니다.",
    "technologies": [{"technology": "Java", "level": 3}],
    "interests": ["WEB"],
    "preferredRoles": ["BACKEND"]
  }
]
```

비로그인 또는 유효하지 않은 계정 세션은 `401 UNAUTHENTICATED`입니다.

---

## 13. 프로필 북마크와 프로젝트 스크랩

모든 요청은 로그인 세션을 사용하며 각 사용자의 저장 목록에 적용됩니다. 추가와 취소는 요청 본문 없이 호출합니다.

| 동작 | 요청 | 성공 |
|---|---|---|
| 프로필 북마크 추가 | `POST /profiles/{targetProfileId}/bookmarks` | `204 No Content` |
| 프로필 북마크 취소 | `DELETE /profiles/{targetProfileId}/bookmarks` | `204 No Content` |
| 내 북마크 목록 | `GET /me/bookmarked-profiles` | `200 OK` |
| 프로젝트 스크랩 추가 | `POST /projects/{projectId}/scraps` | `204 No Content` |
| 프로젝트 스크랩 취소 | `DELETE /projects/{projectId}/scraps` | `204 No Content` |
| 내 스크랩 목록 | `GET /me/scrapped-projects` | `200 OK` |

이미 저장된 대상을 추가하거나 저장하지 않은 대상을 취소해도 `204`입니다. 목록은 저장 ID 내림차순이며 결과가 없으면 `[]`입니다.

북마크 목록 예시:

```json
[
  {"profileId": 12, "isPublic": true, "name": "홍길동"},
  {"profileId": 7, "isPublic": false, "name": "비공개 프로필"}
]
```

북마크한 프로필이 비공개로 전환되면 저장은 유지되며 ID, 공개 상태와 고정 표시명만 반환합니다. 스크랩 목록은 9절의 프로젝트 목록과 같은 기본 정보 객체 배열입니다.

| 상황 | 응답 |
|---|---|
| 비로그인 또는 유효하지 않은 계정 세션 | `401 UNAUTHENTICATED` |
| 비공개 프로필에 새 북마크 추가 | `403 PRIVATE_PROFILE` |
| 존재하지 않는 프로필에 북마크 추가 | `404 PROFILE_NOT_FOUND` |
| 존재하지 않는 프로젝트에 스크랩 추가 | `404 PROJECT_NOT_FOUND` |
