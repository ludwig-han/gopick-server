# GoPick API

GoPick 프론트엔드와 백엔드가 공유하는 현재 API 계약입니다.

- 기준: `gopick-server` 최신 `main`
- 인증: 세션 기반(`JSESSIONID`)
- Staging: `https://3-39-65-219.nip.io`
- 로컬: `http://localhost:8080`

구현과 문서가 다르면 실제 서버 코드를 우선하며, API 변경 시 이 문서를 함께 갱신합니다.

---

## 1. 공통 규칙

### 인증

회원가입 또는 로그인 성공 시 서버가 세션을 생성하고 `JSESSIONID` 쿠키를 발급합니다.

브라우저에서는 인증이 필요한 요청에 쿠키를 포함해야 합니다.

```ts
fetch(`${BASE_URL}/me`, {
  credentials: 'include',
});
```

기본 로컬 CORS 허용 Origin은 다음과 같습니다.

- `http://localhost:5173`
- `http://127.0.0.1:5173`

### 공통 오류

주요 오류 코드는 다음과 같습니다.

| HTTP | code | 의미 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | ID, enum, request body 등 입력 오류 |
| 401 | `UNAUTHENTICATED` | 로그인 세션이 없거나 유효하지 않음 |
| 403 | `PROJECT_ACCESS_DENIED`, `PRIVATE_PROFILE` | 권한 또는 공개 범위 위반 |
| 404 | `*_NOT_FOUND` | 대상 리소스가 없음 |
| 409 | `*_CONFLICT`, `INVALID_PROJECT_STATE` | 현재 상태에서 동작할 수 없음 |

프론트에서는 메시지 문자열보다 `code`를 기준으로 분기합니다.

---

## 2. 전체 Endpoint

| 영역 | Method | Endpoint | 설명 |
|---|---|---|---|
| Account | POST | `/accounts` | 회원가입 + 자동 로그인 |
| Auth | POST | `/auth/login` | 로그인 |
| Auth | POST | `/auth/logout` | 로그아웃 |
| Account | GET | `/me` | 현재 사용자 |
| Account | DELETE | `/me` | 회원 탈퇴 |
| Profile | POST | `/profiles` | 프로필 생성 |
| Profile | GET | `/profiles/{profileId}` | 프로필 상세 |
| Profile | PATCH | `/profiles/{profileId}` | 프로필 수정 |
| Profile | GET | `/profiles` | 공개 프로필 검색 |
| Bookmark | POST / DELETE | `/profiles/{targetProfileId}/bookmarks` | 프로필 북마크 추가 / 취소 |
| Bookmark | GET | `/me/bookmarked-profiles` | 내 북마크 |
| Project | POST | `/projects` | 프로젝트 생성 |
| Project | GET | `/projects` | 프로젝트 목록 |
| Project | GET | `/me/projects` | 내 프로젝트 |
| Project | GET | `/projects/{projectId}` | 프로젝트 상세 |
| Project | PATCH | `/projects/{projectId}` | 프로젝트 수정 |
| Project | POST | `/projects/{projectId}/start` | 프로젝트 시작 |
| Project | PATCH | `/projects/{projectId}/stage` | 진행 단계 변경 |
| Project | POST | `/projects/{projectId}/complete` | 프로젝트 완료 |
| Project | PATCH | `/projects/{projectId}/leader` | 팀장 위임 |
| Project | DELETE | `/projects/{projectId}/members/me` | 프로젝트 하차 |
| Project | DELETE | `/projects/{projectId}` | 프로젝트 삭제 |
| Scrap | POST / DELETE | `/projects/{projectId}/scraps` | 프로젝트 스크랩 추가 / 취소 |
| Scrap | GET | `/me/scrapped-projects` | 내 스크랩 |
| Participation | POST | `/projects/{projectId}/participation-requests` | APPLY / OFFER 생성 |
| Participation | PATCH | `/projects/{projectId}/participation-requests/{requestId}` | 수락 / 거절 / 취소 |
| Participation | GET | `/me/participation-requests/sent` | 보낸 요청 |
| Participation | GET | `/me/participation-requests/received` | 받은 요청 |
| Recommendation | GET | `/projects/{projectId}/recommendations?role=BACKEND` | 프로젝트 기반 팀원 추천 |
| Recruit post | POST | `/projects/recruit-post/draft` | AI 모집글 초안 생성 |
| Notification | GET | `/me/notifications` | 알림 목록 |
| Notification | PATCH | `/me/notifications/{notificationId}/read` | 읽음 처리 |

---

## 3. Account / Auth

### 회원가입

```http
POST /accounts
```

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다.

성공: `201 Created`

```json
{
  "accountId": 1,
  "email": "user@example.com",
  "profileId": null
}
```

회원가입 성공 시 세션도 생성됩니다.

### 로그인

```http
POST /auth/login
```

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

성공: `200 OK` + 현재 사용자 정보.

### 로그아웃

```http
POST /auth/logout
```

성공: `204 No Content`. 이미 세션이 없어도 같은 응답입니다.

### 현재 사용자

```http
GET /me
```

```json
{
  "accountId": 1,
  "email": "user@example.com",
  "profileId": 3
}
```

프로필이 없으면 `profileId`는 `null`입니다.

### 회원 탈퇴

```http
DELETE /me
```

성공: `204 No Content` 후 현재 세션을 무효화합니다.

진행 중 프로젝트의 LEADER는 다른 MEMBER에게 팀장 권한을 위임하기 전에는 탈퇴할 수 없습니다. 탈퇴 시 데이터 정리 규칙은 [DOMAIN.md](DOMAIN.md)를 참고합니다.

---

## 4. Profile

### 생성 / 수정 Request

생성:

```http
POST /profiles
```

수정:

```http
PATCH /profiles/{profileId}
```

두 API는 같은 Profile 본문 구조를 사용합니다.

```json
{
  "name": "홍길동",
  "major": "컴퓨터공학",
  "contactPlatform": "DISCORD",
  "contactId": "gildong",
  "isPublic": true,
  "teamTendency": "FOLLOW",
  "intro": "백엔드 개발에 관심이 있습니다.",
  "parts": ["BACKEND"],
  "interests": ["WEB"],
  "preferredRoles": ["BACKEND"],
  "technologies": [
    {
      "technology": "Java",
      "level": 3
    }
  ],
  "preferences": {
    "collaboration": ["빠른 피드백"],
    "styles": [],
    "strengths": [],
    "projectTendencies": [],
    "communication": []
  },
  "experiences": [
    {
      "types": ["TEAM_PROJECT"],
      "fields": ["WEB"],
      "roles": ["BACKEND"],
      "technologies": ["Java"],
      "description": "팀 프로젝트 백엔드 구현"
    }
  ]
}
```

`name`, `major`, `contactPlatform`, `contactId`, `isPublic`, `parts`, `interests`, `preferredRoles`, `technologies`는 필수입니다. 기술 숙련도는 1~4입니다.

### 상세 조회

```http
GET /profiles/{profileId}
```

- 본인 프로필: 공개 여부와 무관하게 조회 가능하며 연락처 포함
- 다른 사용자의 공개 프로필: 조회 가능, `contactPlatform` / `contactId` 미포함
- 다른 사용자의 비공개 프로필: `403 PRIVATE_PROFILE`

### 공개 프로필 검색

```http
GET /profiles?technology=Java&interest=WEB&preferredRole=BACKEND
```

세 필터는 선택이며 공개 프로필만 검색합니다.

### 프로필 북마크

```http
POST /profiles/{targetProfileId}/bookmarks
DELETE /profiles/{targetProfileId}/bookmarks
GET /me/bookmarked-profiles
```

추가 / 취소 성공은 `204 No Content`입니다.

북마크한 프로필이 비공개로 바뀌면 목록에서 이름 대신 `비공개 프로필`로 표시합니다.

---

## 5. Project

### Project enum

```text
ProjectRole       = PM | FRONTEND | BACKEND | AI | DESIGN | OTHERS
ProjectStatus     = PREPARING | IN_PROGRESS | COMPLETED
ProjectStage      = PLANNING | DESIGN | IMPLEMENTATION | TESTING | PRODUCTION
ConditionMode     = NONE | PREFERRED | REQUIRED
AttributeCategory = FIELD | PURPOSE | COLLAB
```

### 생성

```http
POST /projects
```

```json
{
  "title": "GoPick",
  "description": "프로젝트에 필요한 팀원을 찾는 서비스",
  "background": "팀원 탐색 과정이 번거로워 시작했습니다.",
  "goal": "조건에 맞는 팀원을 빠르게 찾습니다.",
  "wish": "문제가 생기면 바로 공유하는 분",
  "extra": "",
  "post": "작성자가 최종 편집한 모집글",
  "role": "BACKEND",
  "recruiting": true,
  "attributes": [
    {"category": "FIELD", "value": "WEB"},
    {"category": "PURPOSE", "value": "포트폴리오"},
    {"category": "COLLAB", "value": "빠른 피드백"}
  ],
  "experience": {
    "mode": "PREFERRED",
    "types": ["TEAM_PROJECT"],
    "fields": ["WEB"]
  },
  "recruitments": [
    {
      "role": "BACKEND",
      "remainingCount": 1,
      "techMode": "PREFERRED",
      "technologies": [
        {"technology": "Java", "minLevel": 2}
      ]
    }
  ],
  "links": [
    {"type": "GitHub", "url": "https://github.com/example/project"}
  ]
}
```

필수 필드는 `title`, 생성자의 `role`, `recruiting`입니다. 생성자는 해당 역할의 `LEADER`가 됩니다.

`post`는 AI가 직접 저장하는 값이 아니라 작성자가 초안을 확인·수정한 뒤 프로젝트 생성 또는 수정 API로 저장하는 모집글 본문입니다.

### 수정

```http
PATCH /projects/{projectId}
```

LEADER만 호출할 수 있습니다.

보낸 필드만 수정합니다. 기본 텍스트, `post`, `recruiting`, 모집 조건을 수정할 수 있습니다. 모집 관련 배열을 보내면 해당 목록 전체를 교체하고, 생략하면 유지합니다. 명시적인 `null`은 허용하지 않습니다.

### 목록 / 내 프로젝트 / 상세

```http
GET /projects?status=PREPARING&recruiting=true
GET /me/projects
GET /projects/{projectId}
```

`GET /projects`의 `status`, `recruiting`은 선택 필터이며 함께 사용하면 AND 조건입니다.

목록 응답은 다음 정보를 포함합니다.

- 기본 정보: `projectId`, `title`, `description`, `background`, `goal`, `wish`, `extra`, `post`
- 상태: `lifecycleStatus`, `stage`, `recruiting`
- 시각: `createdAt`, `statusUpdatedAt`, `stageUpdatedAt`
- `members`, `recruitments`, `attributes`

`GET /me/projects`에는 `myAuthority`(`LEADER` / `MEMBER`)가 추가됩니다.

상세 응답에는 `experience`, `links`도 포함됩니다.

### 시작 / Stage / 완료

```http
POST  /projects/{projectId}/start
PATCH /projects/{projectId}/stage
POST  /projects/{projectId}/complete
```

Stage 변경:

```json
{"stage": "IMPLEMENTATION"}
```

- 시작: LEADER만, `PREPARING -> IN_PROGRESS`, 동시에 `recruiting=false`
- Stage 변경: IN_PROGRESS의 LEADER / MEMBER
- 완료: LEADER만, `IN_PROGRESS -> COMPLETED`
- 마지막 Stage에 도달해도 자동 완료되지 않음

### 팀장 위임

```http
PATCH /projects/{projectId}/leader
```

```json
{"targetProfileId": 21}
```

IN_PROGRESS 프로젝트의 현재 LEADER가 같은 프로젝트의 MEMBER에게 위임합니다. 성공: `204 No Content`.

### 하차 / 삭제

```http
DELETE /projects/{projectId}/members/me
DELETE /projects/{projectId}
```

상태별 하차 / 삭제 규칙은 [DOMAIN.md](DOMAIN.md)를 참고합니다.

### 프로젝트 스크랩

```http
POST   /projects/{projectId}/scraps
DELETE /projects/{projectId}/scraps
GET    /me/scrapped-projects
```

추가 / 취소 성공: `204 No Content`.

---

## 6. ParticipationRequest

### 생성

```http
POST /projects/{projectId}/participation-requests
```

APPLY:

```json
{
  "type": "APPLY",
  "role": "BACKEND"
}
```

OFFER:

```json
{
  "type": "OFFER",
  "role": "DESIGN",
  "targetProfileId": 42
}
```

- APPLY: 본인이 공개 모집 중인 PREPARING 프로젝트에 지원
- OFFER: 프로젝트 LEADER가 특정 프로필에 참여 제안
- 동일 프로젝트 / 동일 참여자에 PENDING 요청이 이미 있으면 새 요청을 만들 수 없음
- 프로젝트에 이미 참여 중인 사용자에게 요청할 수 없음

성공: `201 Created` + PENDING 요청.

### 처리 / 취소

```http
PATCH /projects/{projectId}/participation-requests/{requestId}
```

```json
{"status": "ACCEPTED"}
```

가능 상태:

```text
ACCEPTED | REJECTED | CANCELED
```

- APPLY 수락 / 거절: 프로젝트 LEADER
- OFFER 수락 / 거절: 제안을 받은 사용자
- CANCELED: 요청을 보낸 사용자
- ACCEPTED 시 요청의 `role`로 ProjectMember가 생성됨

### 목록

```http
GET /me/participation-requests/sent
GET /me/participation-requests/received
```

항목 예시:

```json
{
  "requestId": 11,
  "projectId": 1,
  "projectTitle": "GoPick",
  "type": "APPLY",
  "role": "BACKEND",
  "status": "PENDING",
  "senderProfileId": 42,
  "participantProfileId": 42,
  "createdAt": "2026-11-15T12:00:00",
  "resolvedAt": null
}
```

---

## 7. Recommendation

```http
GET /projects/{projectId}/recommendations?role=BACKEND
```

해당 프로젝트의 LEADER만 호출할 수 있습니다.

서버는 먼저 DB에서 다음을 처리합니다.

- 요청 역할의 모집 조건 확인
- 공개 Profile만 후보로 사용
- 현재 프로젝트 멤버 제외
- REQUIRED 기술 / 경험 조건을 만족하지 않는 후보 제외

그 뒤 AI가 후보의 적합도를 평가하고, 서버가 AI 결과의 후보 ID·중복·점수 범위 등을 검증한 뒤 순위를 반환합니다. 내부 점수는 API에 노출하지 않습니다.

응답 예시:

```json
{
  "projectId": 6,
  "role": "BACKEND",
  "items": [
    {
      "profileId": 3,
      "name": "홍길동",
      "rank": 1,
      "matched": [
        {"category": "STACK", "text": "Java 기술 조건과 잘 맞음"}
      ],
      "unmatched": [
        {"category": "COLLAB", "text": "협업 선호 정보가 다름"}
      ],
      "summary": "기술 조건과 역할 선호가 전반적으로 잘 맞습니다."
    }
  ]
}
```

Reason category:

```text
STACK | COLLAB | EXPERIENCE | ROLE | FIELD | PURPOSE | MAJOR | PART
```

후보가 없으면 `items: []`을 반환합니다.

---

## 8. AI 모집글 초안

```http
POST /projects/recruit-post/draft
```

로그인 세션만 있으면 프로젝트를 저장하기 전에도 호출할 수 있습니다.

```json
{
  "title": "GoPick",
  "description": "프로젝트에 필요한 팀원을 찾는 서비스",
  "fields": ["웹"],
  "purposes": ["포트폴리오"],
  "recruitments": [
    {
      "role": "BACKEND",
      "remainingCount": 1,
      "techMode": "PREFERRED",
      "technologies": [
        {"technology": "Java", "minLevel": 2}
      ]
    }
  ],
  "collaborations": ["코드 리뷰", "빠른 피드백"],
  "background": "프로젝트 팀원을 찾는 과정이 번거로워 시작했습니다.",
  "goal": "조건에 맞는 팀원을 빠르게 찾는 서비스를 완성합니다.",
  "wish": "문제가 생기면 바로 공유하고 함께 해결하는 분이면 좋겠습니다."
}
```

응답:

```json
{
  "post": "GoPick 팀원 모집\n\n프로젝트 소개...\n\n■ 프로젝트 목표\n..."
}
```

이 API는 **초안만 생성하며 DB에 저장하지 않습니다.** 사용자가 편집한 최종 본문은 `POST /projects` 또는 `PATCH /projects/{projectId}`의 `post`로 저장합니다.

---

## 9. Notification

### 목록

```http
GET /me/notifications
```

응답 항목:

```json
{
  "id": 10,
  "type": "APPLY_RECEIVED",
  "projectId": 3,
  "projectTitle": "GoPick",
  "requestId": 11,
  "requestAvailable": true,
  "actorName": "홍길동",
  "createdAt": "2026-11-18T10:00:00",
  "read": false,
  "readAt": null
}
```

현재 Notification type:

```text
APPLY_RECEIVED
OFFER_RECEIVED
APPLY_ACCEPTED
APPLY_REJECTED
OFFER_ACCEPTED
OFFER_REJECTED
LEADER_TRANSFERRED
PROJECT_STARTED
PROJECT_COMPLETED
PROJECT_STAGE_CHANGED
MEMBER_LEFT
PROJECT_DELETED
```

요청 취소는 별도 알림을 만들지 않습니다.

### 읽음 처리

```http
PATCH /me/notifications/{notificationId}/read
```

본인 알림만 처리할 수 있으며 성공: `204 No Content`.
