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

---

## 3. 계정 API

### 회원가입

```text
POST /accounts
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
  "roles": ["Backend"],
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

| 필드 | 조건 |
|---|---|
| `name` | 빈 문자열 불가 |
| `major` | 빈 문자열 불가 |
| `contactPlatform` | 빈 문자열 불가 |
| `contactId` | 빈 문자열 불가 |
| `isPublic` | 필수 |
| `parts` | 최소 1개 |
| `technologies` | 최소 1개 |
| `interests` | 최소 1개 |
| `roles` | 최소 1개 |

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

Response 예시:

```json
{
  "profileId": 3,
  "name": "김수뭉",
  "major": "컴퓨터과학과",
  "contactPlatform": "디스코드",
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

### 연락처 주의

상세조회 응답에는 `contactPlatform`은 있지만 `contactId`는 포함되지 않습니다.

현재 프론트 문구 기준으로 실제 연락처는 **지원·제안이 수락된 상대에게만 공개**하는 정책입니다.

---

## 6. 현재 프론트 `ProfileForm`과 API 차이

현재 `gopick-web`의 실제 `ProfileForm`과 백엔드 API는 일부 이름과 구조가 다릅니다.

### 기본 필드

| 프론트 | API |
|---|---|
| `name` | `name` |
| `major` | `major` |
| `contactType` | `contactPlatform` |
| `contactValue` | `contactId` |
| `isPublic` | `isPublic` |
| `parts` | `parts` |
| `fields` | `interests` |
| `wantedRoles` | `roles` |
| `teamTendency` | `teamTendency` |
| `intro` | `intro` |

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
