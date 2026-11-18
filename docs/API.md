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

프론트 개발 서버의 `/api` 프록시와 배포 환경의 API 주소 설정은 [OPERATIONS.md](OPERATIONS.md#프론트-연동)를 참고합니다. 위 Staging 주소의 배포 버전은 `main`과 별도로 확인합니다.

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
| Project | GET / POST | `/projects/{projectId}/tasks` | 과제 조회 / 단계별 추가 |
| Project | PATCH / DELETE | `/projects/{projectId}/tasks/{taskId}` | 과제 완료 변경 / 삭제 |
| Project | GET / POST | `/projects/{projectId}/activities` | 활동 메모 조회 / 추가 |
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

진행 중 프로젝트의 LEADER는 팀장 권한을 위임하기 전까지 탈퇴 요청에서 `409 Conflict`를 받습니다. 이 경우 HTTP 상태로 분기합니다. 탈퇴 시 데이터 정리 규칙은 [DOMAIN.md](DOMAIN.md)를 참고합니다.

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

생성 성공 시 `201 Created`와 `{"profileId":3}`을 반환합니다. 본인 프로필 수정 성공 시 `204 No Content`를 반환합니다.

### 상세 조회

```http
GET /profiles/{profileId}
```

성공 시 `200 OK`를 반환합니다. 본인은 공개 여부와 관계없이 조회할 수 있고 `contactPlatform`과 `contactId`를 받습니다. 다른 사용자의 공개 프로필을 조회하면 두 연락처 필드가 생략되며, 비공개 프로필 조회는 `403 PRIVATE_PROFILE`을 반환합니다.

본인 프로필 조회 응답 예시

```json
{
  "profileId": 3,
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
  "technologies": [{"technology": "Java", "level": 3}],
  "preferences": [{"category": "COLLAB", "value": "빠른 피드백"}],
  "experiences": [
    {
      "experienceId": 5,
      "description": "팀 프로젝트 백엔드 구현",
      "attributes": [
        {"category": "TYPE", "value": "TEAM_PROJECT"},
        {"category": "FIELD", "value": "WEB"},
        {"category": "ROLE", "value": "BACKEND"},
        {"category": "TECHNOLOGY", "value": "Java"}
      ]
    }
  ]
}
```

상세 조회의 `preferences`와 `experiences[].attributes`는 `category`와 `value`를 가진 항목의 배열입니다. 값이 없는 목록은 `[]`입니다.

| 상세 조회 배열 | `category` 값 |
|---|---|
| `preferences` | `COLLAB`, `STYLE`, `STRENGTH`, `PROJECT`, `COMM` |
| `experiences[].attributes` | `TYPE`, `FIELD`, `ROLE`, `TECHNOLOGY` |

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

`post`는 작성자가 초안을 확인·수정한 뒤 프로젝트 생성 또는 수정 API로 저장하는 모집글 본문입니다. 저장할 본문은 유효한 UTF-8로 인코딩했을 때 65,535바이트 이하여야 합니다. 한도를 초과하거나 인코딩할 수 없는 값은 `400 VALIDATION_ERROR`를 반환하며 `errors.post`에 메시지가 담깁니다.

### 수정

```http
PATCH /projects/{projectId}
```

LEADER만 호출할 수 있습니다.

보낸 필드만 수정합니다. 기본 텍스트, `post`, `recruiting`, 모집 조건을 수정할 수 있습니다. 모집 관련 배열을 보내면 해당 목록 전체를 교체하고, 생략하면 유지합니다. `post`에 빈 문자열을 보내 모집글을 비울 수 있습니다. 명시적인 `null`은 허용하지 않습니다.

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

두 목록은 프로젝트 ID 내림차순으로 반환하며 결과가 없으면 `[]`입니다. `GET /me/projects`는 로그인한 사용자가 속한 프로젝트만 반환하고 `myAuthority`(`LEADER` / `MEMBER`)를 추가합니다. 프로필이 없거나 소속 프로젝트가 없으면 `[]`입니다.

두 목록과 상세 응답이 공유하는 배열의 예시

```json
{
  "members": [
    {
      "profileId": 3,
      "name": "홍길동",
      "isPublic": true,
      "role": "BACKEND",
      "authority": "LEADER",
      "joinedAt": "2026-11-15T12:00:00"
    }
  ],
  "recruitments": [
    {
      "role": "BACKEND",
      "remainingCount": 2,
      "techMode": "REQUIRED",
      "technologies": [{"technology": "Java", "minLevel": 3}]
    }
  ],
  "attributes": [
    {"category": "FIELD", "value": "WEB"},
    {"category": "PURPOSE", "value": "포트폴리오"}
  ]
}
```

값이 없는 목록은 `[]`입니다. 다른 사용자의 비공개 팀원 프로필은 `members[].name`이 `null`입니다. 완료 프로젝트의 탈퇴 회원은 `profileId: null`, `name: "탈퇴한 회원입니다"`, `isPublic: false`로 표시하며 역할, 권한, 참여 시각은 유지합니다. `post`는 모집글을 저장하지 않았으면 `null`일 수 있습니다. 진행 단계를 정하기 전의 `stage`와 `stageUpdatedAt`도 `null`입니다.

상세 응답은 프로젝트 객체 하나를 반환하며 `experience`와 `links`가 추가됩니다. `experience`는 `mode`, `types`, `fields`로 구성되며, `links`의 각 항목은 `type`과 `url`을 가집니다.

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

### 단계별 과제와 활동 기록

조회는 로그인한 모든 사용자가 할 수 있습니다. 추가·변경·삭제는 `IN_PROGRESS` 프로젝트의 LEADER 또는 MEMBER만 가능합니다. `stage`는 `PLANNING`(기획), `DESIGN`(설계), `IMPLEMENTATION`(개발), `TESTING`(테스트), `PRODUCTION`(배포) 중 하나입니다. 현재 진행 단계와 관계없이 각 단계의 과제를 관리할 수 있습니다.

```http
GET    /projects/{projectId}/tasks
POST   /projects/{projectId}/tasks
PATCH  /projects/{projectId}/tasks/{taskId}
DELETE /projects/{projectId}/tasks/{taskId}
GET    /projects/{projectId}/activities
POST   /projects/{projectId}/activities
```

과제 생성 요청은 `{"stage":"DESIGN","text":"화면 설계"}`이며 `201 Created`와 다음 항목을 반환합니다. 과제 `GET`은 모든 단계의 과제를 생성 순으로 반환합니다.

```json
{"id": 12, "stage": "DESIGN", "text": "화면 설계", "done": false}
```

완료 체크 및 해제는 `PATCH`에 `{"done":true}` 또는 `{"done":false}`를 보내고, 변경된 항목을 `200 OK`로 받습니다. 삭제는 `204 No Content`입니다. 존재하지 않거나 다른 프로젝트의 과제 ID는 `404 STAGE_TASK_NOT_FOUND`입니다.

활동 기록 생성 요청은 `{"text":"로그인 화면을 완성했습니다"}`이며 `201 Created`와 다음 항목을 반환합니다. 날짜와 작성자는 서버가 정하며 목록은 최신 생성 순서(ID 내림차순)입니다.

```json
{"id": 7, "date": "2026-11-19", "author": "홍길동", "text": "로그인 화면을 완성했습니다"}
```

과제 `text`는 최대 255자, 활동 기록 `text`는 최대 100자입니다. 공백만 있거나 줄바꿈을 포함할 수 없고 양끝 공백은 저장 시 제거합니다. 활동 기록 `date`는 서버 시간대의 날짜(`YYYY-MM-DD`)입니다. 작성자 이름은 현재 프로필 이름을 사용하므로 프로필 이름 변경이 조회에도 반영됩니다. 작성자가 회원 탈퇴하면 메모는 유지되고 이름은 `탈퇴한 회원입니다`로 표시됩니다. 활동 기록은 사용자가 직접 작성한 메모이며 과제 변경 시 자동 생성되지는 않습니다.

`GET /projects/{projectId}`의 상세 응답에도 `tasks`와 `log`가 포함됩니다. `tasks`는 위 단계 순서대로 항상 다섯 배열이며 각 배열의 과제는 생성 순서입니다. `log`는 위 활동 기록 항목의 최신순 배열입니다. 값이 없으면 다섯 개의 빈 과제 배열과 빈 기록 배열을 반환합니다.

```json
{"tasks": [[], [{"id": 12, "stage": "DESIGN", "text": "화면 설계", "done": false}], [], [], []],
 "log": [{"id": 7, "date": "2026-11-19", "author": "홍길동", "text": "로그인 화면을 완성했습니다"}]}
```

비구성원의 변경은 `403 PROJECT_ACCESS_DENIED`, 삭제됐거나 없는 프로젝트는 `404 PROJECT_NOT_FOUND`, 진행 중이 아닌 프로젝트에 대한 변경은 `409 INVALID_PROJECT_STATE`입니다. 잘못된 단계·ID·본문은 `400 VALIDATION_ERROR`입니다.

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

해당 프로젝트의 LEADER만 호출할 수 있습니다. `role`은 필수이며 해당 프로젝트가 모집하는 역할이어야 합니다. 지원하는 역할 값과 일치하지 않거나 해당 역할의 모집 조건이 없으면 `400 VALIDATION_ERROR`를 반환합니다.

서버는 먼저 DB에서 다음을 처리합니다.

- 요청 역할의 모집 조건 확인
- 공개 Profile만 후보로 사용
- 현재 프로젝트 멤버 제외
- REQUIRED 기술 / 경험 조건을 만족하지 않는 후보 제외. 경험 조건이 저장되지 않은 프로젝트는 `NONE`으로 처리합니다.

필수 조건을 충족한 후보 중 프로필 ID 내림차순으로 최대 20명을 선별합니다. AI가 선별된 후보의 적합도를 평가하고, 서버는 결과의 후보 ID·중복·점수 범위 등을 검증한 뒤 순위를 반환합니다. 내부 점수는 API에 노출하지 않습니다. 실제 Upstage 호출 시 제한과 오류 응답은 [AI 오류와 제한](#ai-오류와-제한)을 참고합니다.

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

후보가 없으면 `items: []`을 반환합니다. `summary`가 없는 항목은 해당 필드를 생략합니다.

---

## 8. AI 모집글 초안

```http
POST /projects/recruit-post/draft
```

유효한 로그인 세션이 있으면 프로젝트를 저장하기 전에도 호출할 수 있습니다. 생성에 필요한 서버 환경변수는 [OPERATIONS.md](OPERATIONS.md#2-환경변수)를 참고합니다.

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

요청 본문에는 공백이 아닌 `title`이 필요합니다. 아래 길이 제한은 Java 문자열의 UTF-16 코드 단위입니다.

| 필드 | 제한 |
|---|---|
| `title`, `description`, `background`, `goal`, `wish` | 각각 최대 255 |
| `fields`, `purposes`, `collaborations` | 배열마다 최대 20개. 각 값은 공백이 아닌 문자열이며 최대 255입니다. |
| `recruitments` | 최대 6개. 각 항목의 `role`, `remainingCount`, `techMode`는 필수이며 `remainingCount`는 0 이상입니다. |
| `recruitments[].technologies` | 모집 항목마다 최대 10개. 각 `technology`는 공백이 아닌 최대 255 길이의 문자열이고 `minLevel`은 1~4입니다. |

선택 목록은 생략하거나 `[]`로 보낼 수 있습니다.

성공 시 `200 OK`를 반환합니다.

```json
{
  "post": "GoPick 팀원 모집\n\n프로젝트 소개...\n\n■ 프로젝트 목표\n..."
}
```

이 API는 **초안만 생성하며 DB에 저장하지 않습니다.** 사용자가 편집한 최종 본문은 `POST /projects` 또는 `PATCH /projects/{projectId}`의 `post`로 저장합니다.

### AI 오류와 제한

Upstage에 요청하는 경우 기본 연결 제한은 5초, 읽기 제한은 20초입니다. 출력 한도는 팀원 추천 8,192토큰, 모집글 초안 2,048토큰입니다.

| 상황 | 응답 |
|---|---|
| Upstage에 보낼 JSON 본문이 96 KiB를 초과 | `400 VALIDATION_ERROR`, `errors.body`에 메시지 포함 |
| Upstage 요청이 실패했거나 응답 형식이 잘못됨 | `502 AI_UPSTREAM_ERROR` |
| Upstage API 키가 설정되지 않음 | `503 AI_UNAVAILABLE` |
| Upstage 연결 또는 응답 대기 시간 초과 | `504 AI_TIMEOUT` |

---

## 9. Notification

### 목록

```http
GET /me/notifications
```

`200 OK`와 함께 본인 알림을 ID 내림차순으로 반환합니다. 알림이 없으면 `[]`입니다.

```json
[
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
]
```

`actorName`은 알림을 조회한 시점의 행위자 공개 프로필 이름입니다. 비공개 프로필, 회원 탈퇴나 프로필 삭제로 확인할 수 없는 행위자, 행위자 정보가 없는 기존 알림에서는 `null`입니다.

`projectId`와 `projectTitle`은 이벤트 당시 값입니다. 프로젝트가 삭제되어도 기존 알림은 유지되며, 삭제된 프로젝트의 상세 조회는 `404 PROJECT_NOT_FOUND`입니다. 회원 탈퇴 시 탈퇴 계정이 받은 알림은 삭제되고 다른 계정이 받은 알림은 유지됩니다.

### 이벤트와 수신자

| 알림 유형 | 수신자 |
|---|---|
| `APPLY_RECEIVED` | 지원 당시 팀장 |
| `OFFER_RECEIVED` | 제안 대상자 |
| `APPLY_ACCEPTED`, `APPLY_REJECTED` | 지원자 |
| `OFFER_ACCEPTED`, `OFFER_REJECTED` | 최초 제안자. 이후 팀장이 바뀌어도 동일합니다. |
| `LEADER_TRANSFERRED` | 새 팀장 |
| `PROJECT_STARTED`, `PROJECT_COMPLETED` | 변경한 사람을 제외한 당시 팀원 |
| `PROJECT_STAGE_CHANGED` | 진행 단계가 실제로 변경됐을 때 변경한 사람을 제외한 당시 팀원 |
| `MEMBER_LEFT` | 준비 중 또는 진행 중 프로젝트에서 일반 팀원이 하차 API를 호출했을 때 현재 팀장 |
| `PROJECT_DELETED` | 명시적 프로젝트 삭제 또는 준비 중 프로젝트 팀장의 하차·회원 탈퇴로 삭제될 때, 해당 팀장을 제외한 당시 팀원 |

요청 취소와 일반 팀원의 회원 탈퇴는 알림 생성 범위에서 제외합니다.

### 참여 요청 알림 열기

`requestId`와 `requestAvailable`은 참여 요청 관련 알림에서 사용합니다. `requestAvailable`은 해당 요청이 현재 사용자의 보낸 요청 또는 받은 요청 목록에 표시되는지를 나타냅니다. 다른 이벤트에서는 두 값이 `null`입니다.

| 알림 유형 | 이동할 목록 |
|---|---|
| `APPLY_RECEIVED`, `OFFER_RECEIVED` | `GET /me/participation-requests/received` |
| `APPLY_ACCEPTED`, `APPLY_REJECTED`, `OFFER_ACCEPTED`, `OFFER_REJECTED` | `GET /me/participation-requests/sent` |

`requestAvailable`이 `true`이면 해당 목록에서 `requestId`로 항목을 찾습니다. `false`이거나 이동 중 요청이 사라져 항목을 찾을 수 없다면 “확인할 수 없는 요청입니다”를 표시합니다. 승인·거절 권한은 요청 처리 API에서 판단합니다.

### 읽음 처리

```http
PATCH /me/notifications/{notificationId}/read
```

본인 알림의 ID를 양의 정수로 넣어 요청 본문 없이 호출합니다. 성공하면 `204 No Content`를 반환합니다. 이미 읽은 알림에 다시 요청해도 `204`를 반환하며 최초 `readAt`은 유지됩니다.

| 상황 | 응답 |
|---|---|
| 비로그인 또는 유효하지 않은 계정 세션 | `401 UNAUTHENTICATED` |
| 잘못된 `notificationId` | `400 VALIDATION_ERROR`, `errors.notificationId`에 메시지 포함 |
| 존재하지 않거나 다른 계정의 알림 | `404 NOTIFICATION_NOT_FOUND` |
