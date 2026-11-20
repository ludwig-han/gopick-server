# GoPick Server

GoPick의 백엔드 서버입니다.

GoPick은 **기술 스택, 관심사, 프로젝트 경험과 모집 조건을 바탕으로 함께 프로젝트를 진행할 팀원을 찾고 관리할 수 있는 서비스**입니다.

GDGoC 7기 첫 번째 팀 프로젝트로 개발했습니다.

- Service: https://gopick-gdgoc.up.railway.app/
- Frontend: https://github.com/seonyoung05/gopick-web

---

## 주요 기능

### 사용자와 프로필

- 회원가입 / 로그인 / 로그아웃
- 세션 기반 인증 (`JSESSIONID`)
- 프로필 생성 및 수정
- 기술 스택, 관심 분야, 프로젝트 경험, 선호 역할 관리
- 공개 프로필 검색
- 프로필 북마크
- 공개 범위에 따른 연락처 정보 접근 제어

### 프로젝트

- 프로젝트 생성 / 조회 / 수정 / 삭제
- 프로젝트 상태 관리
  - `PREPARING`
  - `IN_PROGRESS`
  - `COMPLETED`
- 프로젝트 진행 단계 관리
  - 기획
  - 설계
  - 개발
  - 테스트
  - 배포
- 단계별 과제 및 활동 기록
- 프로젝트 스크랩
- 팀장 권한 위임 및 팀원 하차

### 팀원 모집

프로젝트 참여 방식은 두 가지로 관리합니다.

- `APPLY`: 사용자가 프로젝트에 직접 지원
- `OFFER`: 프로젝트 리더가 특정 사용자에게 참여 제안

참여 요청은 다음 상태를 가집니다.

```text
PENDING
├── ACCEPTED
├── REJECTED
└── CANCELED
```

수락된 요청은 실제 프로젝트 멤버로 연결됩니다.

### 팀원 추천

프로젝트의 모집 조건을 기준으로 팀원 후보를 추천합니다.

```text
프로젝트 모집 조건
        ↓
백엔드에서 공개 여부 / 기존 팀원 / 필수 조건 필터링
        ↓
AI를 이용한 후보 비교
        ↓
추천 결과와 이유 반환
```

명확하게 판단할 수 있는 권한이나 필수 조건은 애플리케이션에서 처리하고, AI는 후보 간 비교와 추천 이유 생성을 담당합니다.

### AI 모집글 작성

프로젝트 정보를 바탕으로 AI가 팀원 모집글의 초안을 생성합니다.

AI가 작성한 결과를 바로 저장하지 않고,

```text
프로젝트 정보 입력
        ↓
AI 초안 생성
        ↓
사용자 확인 / 수정
        ↓
최종 모집글 저장
```

순서로 동작합니다.

### 알림

다음과 같은 프로젝트 이벤트에 대해 알림을 제공합니다.

- 지원 / 참여 제안
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

- Docker
- Railway

---

## 구조

기본적으로 Controller - Service - Repository 계층으로 구성되어 있습니다.

```text
Client
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
- **Service**: 프로젝트 정책 및 비즈니스 로직 처리
- **Repository**: 데이터 조회 및 저장

인증은 서버 세션을 사용하며 로그인 성공 시 발급되는 `JSESSIONID` 쿠키를 이용합니다.

---

## Documentation

상세한 내용은 `docs/`에서 확인할 수 있습니다.

| 문서 | 내용 |
|---|---|
| [API.md](docs/API.md) | API Endpoint와 Request / Response 계약 |
| [DOMAIN.md](docs/DOMAIN.md) | 프로젝트 상태, 권한, 참여 요청 등 도메인 정책 |
| [OPERATIONS.md](docs/OPERATIONS.md) | 로컬 실행, 테스트, 환경변수, 배포 및 DB 운영 |

DB 변경이 필요한 경우의 SQL은 `docs/migrations/`에 관리합니다.

---

## Local Development

### Requirements

- Java 21
- MySQL

기본 DB 설정은 다음과 같습니다.

```text
jdbc:mysql://localhost:3306/gopick
```

MySQL에 `gopick` 데이터베이스를 생성한 뒤 환경변수를 설정합니다.

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

기본 서버 주소:

```text
http://localhost:8080
```

---

## Environment Variables

| 환경변수 | 설명 |
|---|---|
| `PORT` | 서버 포트, 기본값 `8080` |
| `DB_URL` | MySQL JDBC URL |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `CORS_ALLOWED_ORIGINS` | 허용할 Frontend Origin |
| `SESSION_COOKIE_SAME_SITE` | 세션 쿠키 SameSite 설정 |
| `SESSION_COOKIE_SECURE` | 세션 쿠키 Secure 설정 |
| `RECOMMENDATION_AI_MODE` | AI 추천 모드 (`stub` / `upstage`) |
| `UPSTAGE_API_KEY` | Upstage API Key |
| `UPSTAGE_BASE_URL` | Upstage API 주소 |
| `UPSTAGE_MODEL` | 사용할 Upstage 모델 |

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

## What I Learned

GoPick을 개발하면서 단순한 API 구현뿐 아니라 다음과 같은 문제를 직접 경험하고 고민했습니다.

- 프론트엔드와 백엔드 사이의 API 계약 관리
- 서비스 정책을 데이터 모델과 API로 표현하는 과정
- 세션 인증과 사용자 권한 관리
- 여러 상태가 연결된 도메인 로직과 트랜잭션
- 자동 테스트를 통한 기존 기능 검증
- 실제 배포 환경에서의 Frontend / Backend 연동
- AI가 담당할 영역과 애플리케이션이 책임질 영역의 구분

프로젝트 과정에 대한 자세한 회고는 별도의 글로 정리할 예정입니다.