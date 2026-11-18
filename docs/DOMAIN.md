# GoPick Domain

GoPick 코드와 API에서 사용하는 핵심 도메인, 상태, 권한 규칙을 정리한 문서입니다.

이 문서는 현재 구현 기준이며, API 형식은 [API.md](API.md)를 참고합니다.

---

## 1. Account와 Profile

### Account

로그인과 인증을 위한 계정입니다.

- email
- password
- 생성 시각

### Profile

팀원 검색과 추천에서 사용하는 서비스 프로필입니다.

Account와 Profile은 구분하며 하나의 Account는 최대 하나의 Profile을 가집니다.

Profile에는 이름, 전공, 기술 스택과 숙련도, 관심 분야, 희망 역할, 협업 선호, 프로젝트 경험, 소개 등이 포함됩니다.

### 공개 범위

공개 Profile:

- 검색 가능
- 추천 후보가 될 수 있음
- 다른 사용자가 상세 조회 가능

비공개 Profile:

- 검색 및 추천 후보에서 제외
- 본인만 상세 조회 가능
- 기존 북마크 목록에서는 상세 내용을 숨기고 `비공개 프로필`로 표시

본인이 아닌 사용자가 공개 Profile을 조회할 때 연락처는 노출하지 않습니다.

---

## 2. Project

Project는 팀을 구성하고 진행하는 프로젝트 자체입니다.

### ProjectStatus

```text
PREPARING -> IN_PROGRESS -> COMPLETED
```

#### PREPARING

프로젝트 시작 전 상태입니다.

- `recruiting=true`로 공개 모집 가능
- 일반 사용자가 APPLY 가능
- LEADER가 OFFER 가능
- LEADER가 프로젝트를 시작하면 IN_PROGRESS

#### IN_PROGRESS

프로젝트가 공식적으로 시작된 상태입니다.

- 시작 시 `recruiting=false`
- 공개 APPLY 불가
- LEADER가 특정 사용자에게 OFFER 가능
- LEADER / MEMBER가 Stage 변경 가능
- LEADER 위임 가능

#### COMPLETED

최종 상태입니다.

- 다시 IN_PROGRESS로 되돌리지 않음
- Stage가 마지막 단계에 도달했다고 자동 완료되지는 않음
- 기존 프로젝트 참여 이력을 보존

### recruiting

프로젝트 생명주기와 별개의 공개 모집 여부입니다.

- `true`: 공개 모집 중, APPLY 가능
- `false`: 공개 APPLY 불가

`recruiting=true`는 PREPARING에서만 허용합니다.

### ProjectStage

IN_PROGRESS 프로젝트의 실제 작업 진행 단계를 표현합니다.

```text
PLANNING
DESIGN
IMPLEMENTATION
TESTING
PRODUCTION
```

마지막 Stage에 도달해도 프로젝트 완료는 자동으로 일어나지 않습니다.

---

## 3. ProjectMember

프로젝트에 실제 참여 중인 구성원입니다.

### 역할과 권한은 별개

ProjectRole:

```text
PM | FRONTEND | BACKEND | AI | DESIGN | OTHERS
```

ProjectMemberType:

```text
LEADER | MEMBER
```

예를 들어 `BACKEND + LEADER`, `DESIGN + MEMBER`처럼 역할과 프로젝트 내 권한을 따로 가집니다.

한 프로젝트에는 한 명의 LEADER가 존재합니다.

### 권한 요약

| 기능 | LEADER | MEMBER |
|---|---:|---:|
| 프로젝트 기본 정보 / 모집 조건 수정 | O | X |
| 공개 모집 여부 변경 | O | X |
| 프로젝트 시작 | O | X |
| 프로젝트 완료 | O | X |
| Stage 변경 | O | O |
| APPLY 승인 / 거절 | O | X |
| OFFER 전송 | O | X |
| 팀원 추천 요청 | O | X |
| 팀장 위임 | O | X |
| 프로젝트 삭제 | O | X |
| 본인 하차 | 상태에 따라 가능 | PREPARING / IN_PROGRESS에서 가능 |

---

## 4. 모집 조건

Project는 역할별 Recruitment와 추가 조건을 가집니다.

### ConditionMode

```text
NONE | PREFERRED | REQUIRED
```

- `NONE`: 조건 없음
- `PREFERRED`: 우대 조건
- `REQUIRED`: 필수 조건

기술 조건의 `minLevel`은 Profile 기술 숙련도와 같은 1~4 범위입니다.

### ProjectAttribute

```text
FIELD | PURPOSE | COLLAB
```

프로젝트의 분야, 목적, 협업 선호를 표현합니다.

### Experience

경험 조건도 `NONE / PREFERRED / REQUIRED`를 사용하며 경험 유형과 분야를 저장합니다.

---

## 5. ParticipationRequest

APPLY와 OFFER를 하나의 참여 요청 도메인으로 관리합니다.

### Type

```text
APPLY | OFFER
```

#### APPLY

사용자가 공개 모집 중인 프로젝트에 직접 지원합니다.

조건:

- 프로젝트가 PREPARING
- `recruiting=true`
- 본인 Profile이 존재
- 이미 프로젝트 멤버가 아님
- 동일 프로젝트 / 참여자에 PENDING 요청이 없음

APPLY의 승인 / 거절은 현재 LEADER가 처리합니다.

#### OFFER

LEADER가 특정 Profile에 프로젝트 참여를 제안합니다.

- PREPARING 또는 IN_PROGRESS에서 가능
- 공개 모집 여부와 무관
- 대상 Profile이 이미 멤버이면 불가
- 동일 프로젝트 / 참여자에 PENDING 요청이 있으면 불가

OFFER의 승인 / 거절은 제안받은 사용자가 처리합니다.

### Status

```text
PENDING | ACCEPTED | REJECTED | CANCELED
```

- ACCEPTED: 실제 ProjectMember 생성
- REJECTED: 요청 거절
- CANCELED: 요청을 보낸 쪽에서 취소
- 이미 처리된 요청은 다시 처리할 수 없음

APPLY와 OFFER 모두 요청을 보낸 사용자가 PENDING 상태에서 취소할 수 있습니다.

---

## 6. 프로젝트 시작 / 완료 / 하차 / 삭제

### 시작

LEADER가 실행합니다.

```text
PREPARING -> IN_PROGRESS
recruiting -> false
```

### 완료

LEADER가 실행합니다.

```text
IN_PROGRESS -> COMPLETED
```

### 하차

| 상태 | 권한 | 처리 |
|---|---|---|
| PREPARING | MEMBER | 해당 ProjectMember 제거 |
| PREPARING | LEADER | 프로젝트와 해당 프로젝트 데이터를 hard delete |
| IN_PROGRESS | MEMBER | 해당 ProjectMember 제거 |
| IN_PROGRESS | LEADER | 다른 MEMBER에게 위임 후 하차 가능 |
| COMPLETED | LEADER / MEMBER | 하차 불가, 이력 보존 |

### 명시적 프로젝트 삭제

LEADER가 프로젝트를 명시적으로 삭제할 수 있습니다. 일반 프로젝트 삭제는 서비스 조회에서 제외되도록 처리하며, PREPARING LEADER의 하차는 별도 규칙으로 프로젝트를 물리적으로 삭제합니다.

---

## 7. 회원 탈퇴

회원 탈퇴는 Account, Profile과 관련 데이터를 함께 정리합니다.

주요 규칙:

- 진행 중 프로젝트의 LEADER는 위임 전 탈퇴 불가
- 준비 중 프로젝트의 LEADER가 탈퇴하면 해당 프로젝트 삭제
- 일반 멤버는 준비 중 / 진행 중 프로젝트에서 제거
- 완료 프로젝트의 참여 기록은 보존하고 Profile 연결만 제거
- 지원 / 제안에서 탈퇴 Profile이 발신자 또는 참여자인 기록은 삭제
- 본인의 Profile 북마크 / Project 스크랩 정리
- 탈퇴한 Profile을 가리키는 북마크 등 연관 데이터 정리
- 탈퇴 완료 후 Account 삭제 및 현재 세션 무효화

완료 프로젝트의 탈퇴 회원은 프로젝트 멤버 목록에서 Profile 연결이 없는 상태로 남아 참여 이력을 보존합니다.

---

## 8. Bookmark / Scrap

### Profile Bookmark

사용자가 관심 있는 Profile을 개인적으로 저장합니다.

- 다른 사용자에게 노출하지 않음
- 대상 Profile이 비공개가 되면 북마크 자체는 유지할 수 있으나 상세 정보는 숨김

### Project Scrap

사용자가 관심 있는 Project를 개인적으로 저장합니다.

---

## 9. Notification

참여 요청과 주요 프로젝트 이벤트를 관련 사용자에게 알립니다.

현재 이벤트:

- APPLY / OFFER 도착
- APPLY / OFFER 수락 / 거절
- 팀장 위임
- 프로젝트 시작 / 완료
- Stage 변경
- 팀원 하차
- 프로젝트 삭제

요청 취소(CANCELED)는 알림 생성 범위에서 제외합니다.

알림에는 프로젝트 ID / 제목, 관련 요청 ID, 행위자 이름, 생성 / 읽음 시각 등이 포함됩니다. 관련 ParticipationRequest가 현재 사용자에게 더 이상 보이지 않으면 `requestAvailable=false`가 될 수 있습니다.

알림은 해당 이벤트를 처리하는 트랜잭션 안에서 함께 저장됩니다.

---

## 10. Recommendation

팀원 추천은 프로젝트의 특정 모집 역할을 기준으로 공개 Profile을 비교합니다.

### 호출 권한

해당 프로젝트의 LEADER만 추천을 요청할 수 있습니다.

### 서버가 먼저 결정하는 것

AI 호출 전에 서버가 다음 규칙을 적용합니다.

- 프로젝트와 요청 역할의 Recruitment 확인
- 공개 Profile만 후보로 사용
- 현재 프로젝트 멤버 제외
- REQUIRED 기술 조건 필터
- REQUIRED 경험 조건 필터

즉, 권한과 필수 조건 충족 여부는 AI에게 맡기지 않습니다.

### AI가 담당하는 것

필터링된 후보에 대해 프로젝트 조건과 Profile 정보를 비교하여:

- 내부 적합도 점수
- matched 이유
- unmatched 이유
- summary

를 생성합니다.

### 서버의 후처리

AI 응답을 그대로 노출하지 않습니다.

- 서버가 보낸 후보 ID만 허용
- 후보 중복 금지
- 모든 후보가 정확히 한 번 포함되는지 확인
- 내부 점수 범위 검증
- 점수 기준 정렬 및 rank 계산
- 내부 점수는 최종 API에서 숨김

AI 호출은 DB 읽기 트랜잭션 밖에서 실행해 외부 API 응답을 기다리는 동안 DB 트랜잭션을 유지하지 않습니다.

Reason category:

```text
STACK | COLLAB | EXPERIENCE | ROLE | FIELD | PURPOSE | MAJOR | PART
```

---

## 11. AI 모집글

모집글 생성은 프로젝트 저장과 분리되어 있습니다.

흐름:

```text
프로젝트 폼 입력
-> AI 모집글 초안 생성
-> 사용자 확인 / 편집
-> Project.post 로 저장
```

AI API는 입력된 프로젝트 정보만 바탕으로 초안을 생성하고 DB에 직접 저장하지 않습니다.

따라서 생성 실패가 프로젝트 자체의 저장 로직과 결합되지 않고, 사용자가 AI 문장을 수정한 뒤 최종 텍스트를 저장할 수 있습니다.
