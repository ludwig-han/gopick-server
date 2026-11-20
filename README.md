# GoPick Server

GoPick의 백엔드 서버입니다.

GoPick은 **기술 스택, 관심사, 프로젝트 경험과 모집 조건을 바탕으로 함께 프로젝트를 진행할 팀원을 찾고 관리할 수 있는 서비스**입니다.

GDGoC 7기 첫 번째 팀 프로젝트로 개발했습니다.

## Links

- Frontend Repository: https://github.com/seonyoung05/gopick-web
- Project Retrospective: https://ludwig-han.tistory.com/2

---

## 주요 기능

### 사용자 / 프로필

- 회원가입, 로그인, 로그아웃
- 세션 기반 인증 (`JSESSIONID`)
- 프로필 생성 및 수정
- 기술 스택, 관심 분야, 프로젝트 경험, 선호 역할 관리
- 공개 프로필 검색
- 프로필 북마크
- 공개 범위에 따른 연락처 정보 접근 제어

### 프로젝트

- 프로젝트 생성, 조회, 수정, 삭제
- 프로젝트 스크랩
- 프로젝트 생명주기 관리
  - `PREPARING`
  - `IN_PROGRESS`
  - `COMPLETED`
- 프로젝트 진행 단계 관리
  - `PLANNING`
  - `DESIGN`
  - `IMPLEMENTATION`
  - `TESTING`
  - `PRODUCTION`
- 단계별 과제 및 활동 기록
- 팀장 권한 위임
- 프로젝트 하차

### 팀원 모집

프로젝트 참여는 `APPLY`와 `OFFER` 두 가지 방식으로 이루어집니다.

- `APPLY`: 사용자가 모집 중인 프로젝트에 직접 지원
- `OFFER`: 프로젝트 리더가 특정 사용자에게 참여 제안

참여 요청은 다음 상태를 가집니다.

```text
PENDING
├── ACCEPTED
├── REJECTED
└── CANCELED
```

요청이 수락되면 해당 사용자가 실제 프로젝트 멤버로 추가됩니다.

### 팀원 추천

프로젝트의 모집 조건과 사용자 프로필을 이용해 팀원 후보를 추천합니다.

```text
프로젝트 모집 조건
        ↓
백엔드에서 공개 여부 / 기존 팀원 / 필수 조건 필터링
        ↓
AI를 이용한 후보 비교
        ↓
추천 결과와 이유 반환
```

공개 여부, 현재 프로젝트 구성원 여부, 필수 기술·경험 조건처럼 명확하게 판단할 수 있는 규칙은 백엔드에서 처리하고 AI는 필터링된 후보의 비교와 추천 이유 생성을 담당합니다.

### AI 모집글 작성

사용자가 입력한 프로젝트 정보를 바탕으로 AI가 팀원 모집글의 초안을 생성합니다.

```text
프로젝트 정보 입력
        ↓
AI 초안 생성
        ↓
사용자 확인 / 수정
        ↓
최종 모집글 저장
```

AI가 생성한 내용을 바로 저장하지 않고 사용자가 직접 확인하고 수정한 뒤 최종 모집글을 저장합니다.

### 알림

다음과 같은 이벤트에 대해 알림을 제공합니다.

- 프로젝트 지원 / 참여 제안
- 요청 수락 / 거절
- 프로젝트 시작 / 완료
- 진행 단계 변경
- 팀장 위임
- 팀원 하차
- 프로젝트 삭제

---

## Tech Stack

### Backend

- Java 21
- Spring Boot 4.1.2
- Spring MVC
- Spring Data JPA
- MySQL
- Gradle

### Test

- JUnit
- Spring Boot Test
- MockMvc
- H2

### AI

- Upstage Solar API

### Deployment

- AWS EC2
- Nginx
- systemd

---

## Architecture

백엔드는 Controller - Service - Repository 계층으로 구성했습니다.

```text
Client
  ↓
Nginx
  ↓
Spring Boot
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
MySQL
```

각 계층은 다음 역할을 담당합니다.

- **Controller**: HTTP 요청과 응답 처리
- **Service**: 서비스 정책과 비즈니스 로직 처리
- **Repository**: 데이터 조회 및 저장

배포 환경에서는 AWS EC2에서 Spring Boot 애플리케이션을 실행하고, Nginx를 reverse proxy로 사용했습니다. Spring Boot 프로세스는 systemd 서비스로 등록하여 실행과 재시작을 관리했습니다.

---

## Authentication

GoPick은 서버 세션 기반 인증을 사용합니다.

회원가입 또는 로그인에 성공하면 서버가 세션을 생성하고 브라우저에 `JSESSIONID` 쿠키를 전달합니다.

이후 요청에서는 해당 쿠키를 이용해 로그인한 사용자를 식별합니다.

프론트엔드에서는 세션 쿠키를 함께 전달하기 위해 다음과 같이 요청합니다.

```ts
fetch(url, {
  credentials: 'include',
});
```

---

## Domain

### Project Status

프로젝트 전체 생명주기를 나타냅니다.

```text
PREPARING → IN_PROGRESS → COMPLETED
```

### Project Stage

진행 중인 프로젝트가 현재 어느 단계에 있는지를 나타냅니다.

```text
PLANNING
DESIGN
IMPLEMENTATION
TESTING
PRODUCTION
```

`Status`와 `Stage`는 서로 다른 개념으로 관리하며 마지막 Stage에 도달했다고 프로젝트가 자동으로 완료되지는 않습니다.

### Role / Authority

프로젝트에서 담당하는 역할과 프로젝트 내 권한을 분리했습니다.

Role:

```text
PM
FRONTEND
BACKEND
AI
DESIGN
OTHERS
```

Authority:

```text
LEADER
MEMBER
```

LEADER는 프로젝트 정보 수정, 참여 요청 관리, 프로젝트 시작·완료, 팀장 위임 등 프로젝트 운영에 필요한 추가 권한을 가집니다.

---

## Documentation

상세한 내용은 `docs/`에서 확인할 수 있습니다.

| 문서 | 내용 |
|---|---|
| [API.md](docs/API.md) | API Endpoint와 Request / Response 계약 |
| [DOMAIN.md](docs/DOMAIN.md) | 상태, 권한, 참여 요청 등 도메인 정책 |
| [OPERATIONS.md](docs/OPERATIONS.md) | 로컬 실행, 테스트, 환경변수 및 운영 방법 |

DB 스키마 변경에 필요한 SQL은 `docs/migrations/`에서 관리합니다.

---

## Local Development

### Requirements

- Java 21
- MySQL

MySQL에 로컬 데이터베이스를 생성합니다.

```sql
CREATE DATABASE gopick;
```

기본 JDBC 주소는 다음과 같습니다.

```text
jdbc:mysql://localhost:3306/gopick
```

### Windows

```cmd
set DB_PASSWORD=your-password
gradlew bootRun
```

### Linux / macOS / WSL

```bash
export DB_PASSWORD='your-password'
./gradlew bootRun
```

기본 서버 포트는 `8080`입니다.

```text
http://localhost:8080
```

---

## Environment Variables

| 환경변수 | 설명 | 기본값 |
|---|---|---|
| `PORT` | Spring Boot 서버 포트 | `8080` |
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/gopick` |
| `DB_USERNAME` | DB 사용자 | `root` |
| `DB_PASSWORD` | DB 비밀번호 | - |
| `CORS_ALLOWED_ORIGINS` | 허용할 Frontend Origin | localhost 개발 주소 |
| `SESSION_COOKIE_SAME_SITE` | 세션 쿠키 SameSite 설정 | `lax` |
| `SESSION_COOKIE_SECURE` | 세션 쿠키 Secure 설정 | `false` |
| `RECOMMENDATION_AI_MODE` | AI 추천 모드 | `stub` |
| `UPSTAGE_API_KEY` | Upstage API Key | - |
| `UPSTAGE_BASE_URL` | Upstage API 주소 | `https://api.upstage.ai/v1` |
| `UPSTAGE_MODEL` | 사용할 Upstage 모델 | `solar-pro4` |

API Key와 DB 비밀번호 등 비밀 정보는 저장소에 커밋하지 않습니다.

---

## Test

전체 테스트는 Gradle Wrapper를 이용해 실행할 수 있습니다.

### Windows

```cmd
gradlew test --console=plain
```

### Linux / macOS / WSL

```bash
./gradlew test --console=plain
```

테스트 환경에서는 운영 MySQL 대신 H2 인메모리 데이터베이스를 사용합니다.

---

## Retrospective

프로젝트를 진행하며 단순한 API 구현뿐 아니라 다음과 같은 문제를 직접 경험하고 고민했습니다.

- 서비스 정책을 API와 데이터 모델로 표현하는 과정
- 프론트엔드와 백엔드 사이의 API 계약 관리
- 변경되는 API 계약을 문서화하고 공유하는 방법
- AI가 담당할 영역과 애플리케이션이 책임질 영역의 구분

프로젝트를 진행하며 배운 점과 아쉬웠던 점은 아래 글에 정리했습니다.

**[GoPick 프로젝트 회고](https://ludwig-han.tistory.com/2)**