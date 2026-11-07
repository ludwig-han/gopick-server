# GoPick Staging API 사용 안내

현재 개발 중인 GoPick 백엔드 staging 서버 사용 안내입니다.

## 1. Base URL

```text
https://3-39-65-219.nip.io
```

모든 API 요청은 위 주소를 기준으로 호출합니다.

예:

```text
GET https://3-39-65-219.nip.io/me
```

## 2. 인증 방식

GoPick은 현재 **세션 기반 인증**을 사용합니다.

회원가입 또는 로그인에 성공하면 서버가 `JSESSIONID` 쿠키를 발급합니다. 이후 인증이 필요한 요청에서는 해당 쿠키가 함께 전달되어야 합니다.

프론트엔드에서 `fetch`를 사용할 경우:

```javascript
fetch(`${BASE_URL}/me`, {
  credentials: "include"
});
```

Axios를 사용할 경우:

```javascript
axios.get(`${BASE_URL}/me`, {
  withCredentials: true
});
```

현재 로컬 프론트엔드는 아래 Origin을 허용하고 있습니다.

```text
http://localhost:5173
http://127.0.0.1:5173
```

다른 주소 또는 포트를 사용하는 경우 백엔드 CORS 설정에 추가가 필요합니다.

---

# 3. 현재 사용 가능한 API

## 회원가입

```text
POST /accounts
```

### Request

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

### Success

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

### 주요 실패 응답

```text
400 VALIDATION_ERROR
409 DUPLICATE_EMAIL
```

---

## 로그인

```text
POST /auth/login
```

### Request

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

### Success

```text
200 OK
```

```json
{
  "accountId": 1,
  "email": "user@example.com",
  "profileId": 1
}
```

프로필이 아직 없다면 `profileId`는 `null`입니다.

### 주요 실패 응답

```text
400 VALIDATION_ERROR
401 INVALID_CREDENTIALS
```

이메일이 존재하지 않는 경우와 비밀번호가 틀린 경우 모두 `INVALID_CREDENTIALS`로 응답합니다.

---

## 로그아웃

```text
POST /auth/logout
```

### Success

```text
204 No Content
```

현재 로그인 세션이 없어도 동일하게 `204`를 반환합니다.

---

## 현재 사용자 조회

```text
GET /me
```

로그인 세션이 필요합니다.

### Success

```text
200 OK
```

```json
{
  "accountId": 1,
  "email": "user@example.com",
  "profileId": 1
}
```

프로필이 없다면:

```json
{
  "accountId": 1,
  "email": "user@example.com",
  "profileId": null
}
```

### 실패

로그인하지 않은 경우:

```text
401 UNAUTHENTICATED
```

---

## 프로필 생성

```text
POST /profiles
```

로그인 세션이 필요합니다.

### Request 예시

```json
{
  "name": "홍길동",
  "major": "컴퓨터공학",
  "contactPlatform": "DISCORD",
  "contactId": "example",
  "isPublic": true,
  "teamTendency": "FOLLOW",
  "intro": "백엔드 개발에 관심이 있습니다.",
  "parts": [
    "BACKEND"
  ],
  "interests": [
    "WEB"
  ],
  "roles": [
    "BACKEND"
  ],
  "technologies": [
    {
      "technology": "Java",
      "level": 2
    },
    {
      "technology": "Spring Boot",
      "level": 2
    }
  ]
}
```

`parts`, `interests`, `roles`, `technologies`는 생략할 수 있습니다.

기술 수준 `level`은 현재 `1~4` 범위를 사용합니다.

### Success

```text
201 Created
```

```json
{
  "profileId": 1
}
```

### 주요 실패 응답

```text
400 VALIDATION_ERROR
401 UNAUTHENTICATED
409 DUPLICATE_PROFILE
```

한 계정에는 현재 하나의 프로필만 생성할 수 있습니다.

---

# 4. 공통 에러 형식

일반적인 에러 응답:

```json
{
  "code": "UNAUTHENTICATED",
  "message": "로그인이 필요합니다."
}
```

입력값 검증 실패:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해주세요.",
  "errors": {
    "email": "must be a well-formed email address"
  }
}
```

현재 사용 중인 주요 에러 코드는 다음과 같습니다.

```text
VALIDATION_ERROR
DUPLICATE_EMAIL
INVALID_CREDENTIALS
UNAUTHENTICATED
DUPLICATE_PROFILE
```

# 5. Staging 사용 시 참고

- staging 서버의 데이터는 개발 및 테스트용입니다.
- 개발 과정에서 DB 데이터가 변경되거나 초기화될 수 있습니다.
- 현재 API는 개발 중이므로 요청/응답 형식이 변경될 수 있습니다.
- API 연동 중 서버 오류나 CORS/세션 문제가 발생하면 백엔드 쪽에 공유해주세요.
