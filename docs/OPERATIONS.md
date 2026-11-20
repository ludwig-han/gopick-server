# GoPick Operations

GoPick 백엔드의 로컬 개발, 테스트, 환경변수, 배포 구성과 MySQL 마이그레이션을 정리한 문서입니다.

API 계약은 [API.md](API.md), 도메인 규칙은 [DOMAIN.md](DOMAIN.md)를 참고합니다.

---

## 1. Runtime / Stack

- Java 21
- Spring Boot 4.1.2
- Spring MVC
- Spring Data JPA
- MySQL
- H2 (test)
- Gradle Wrapper

테스트 환경에서는 H2 인메모리 DB를 사용합니다.

프로젝트 운영 당시 백엔드는 AWS EC2에 배포했으며 다음 구조로 운영했습니다.

```text
Client
  ↓
Nginx
  ↓
Spring Boot
  ↓
MySQL
```

Spring Boot 애플리케이션은 `systemd` 서비스로 등록하여 실행과 재시작을 관리했습니다.

---

## 2. 환경변수

`src/main/resources/application.properties` 기준입니다.

| 환경변수 | 필수 | 기본값 / 용도 |
|---|---|---|
| `PORT` | 선택 | HTTP 포트, 기본값 `8080` |
| `DB_URL` | 선택 | JDBC URL, 기본값 `jdbc:mysql://localhost:3306/gopick` |
| `DB_USERNAME` | 선택 | MySQL 사용자, 기본값 `root` |
| `DB_PASSWORD` | DB 연결 시 필수 | MySQL 비밀번호 |
| `CORS_ALLOWED_ORIGINS` | 선택 | 허용할 Frontend Origin |
| `SESSION_COOKIE_SAME_SITE` | 선택 | 기본값 `lax` |
| `SESSION_COOKIE_SECURE` | 선택 | 기본값 `false` |
| `RECOMMENDATION_AI_MODE` | 선택 | 기본값 `stub`, 실제 Upstage 추천은 `upstage` |
| `UPSTAGE_API_KEY` | AI 기능 사용 시 필수 | Upstage API Key |
| `UPSTAGE_BASE_URL` | 선택 | 기본값 `https://api.upstage.ai/v1` |
| `UPSTAGE_MODEL` | 선택 | 기본값 `solar-pro4` |

API Key와 DB 비밀번호 등 secret은 Git에 커밋하지 않습니다.

### EC2 환경변수

운영 당시 서버 환경변수는 다음 파일에서 관리했습니다.

```text
/etc/gopick/gopick.env
```

확인:

```bash
sudo cat /etc/gopick/gopick.env
```

특정 항목만 확인:

```bash
sudo grep -E '^(PORT|CORS_ALLOWED_ORIGINS|SESSION_COOKIE_SAME_SITE|SESSION_COOKIE_SECURE|JAVA_TOOL_OPTIONS)=' /etc/gopick/gopick.env
```

환경변수를 수정한 뒤에는 Spring Boot 프로세스를 재시작합니다.

```bash
sudo systemctl restart gopick
```

### JVM Timezone

주요 시간 필드는 `LocalDateTime.now()`를 사용하므로 JVM 기본 시간대의 영향을 받습니다.

운영 환경에서는 JVM 시간대를 한국 시간으로 설정했습니다.

```text
JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Seoul
```

---

## 3. CORS / Session

GoPick은 `JSESSIONID`를 사용하는 서버 세션 기반 인증을 사용합니다.

프론트엔드에서는 인증 요청에 쿠키를 포함합니다.

```ts
fetch(url, {
  credentials: 'include',
});
```

백엔드에서는 프론트엔드 Origin을 `CORS_ALLOWED_ORIGINS`에 등록합니다.

여러 Origin은 쉼표로 구분합니다.

```text
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://frontend.example.com
```

서로 다른 사이트의 프론트엔드와 백엔드가 세션 쿠키를 사용해 통신하는 경우 다음 설정이 필요할 수 있습니다.

```text
SESSION_COOKIE_SAME_SITE=none
SESSION_COOKIE_SECURE=true
```

`Secure` 쿠키를 사용할 때는 HTTPS 환경이 필요합니다.

---

## 4. 로컬 실행

MySQL에 `gopick` 데이터베이스를 생성합니다.

```sql
CREATE DATABASE gopick;
```

### Windows

```cmd
set DB_PASSWORD=...
set UPSTAGE_API_KEY=...
set RECOMMENDATION_AI_MODE=upstage
gradlew bootRun
```

### Linux / macOS / WSL

```bash
export DB_PASSWORD='...'
export UPSTAGE_API_KEY='...'
export RECOMMENDATION_AI_MODE='upstage'
./gradlew bootRun
```

기본 서버 주소:

```text
http://localhost:8080
```

실제 AI API 없이 추천 기능을 확인하려면 기본값인 `stub` 모드를 사용할 수 있습니다.

---

## 5. 테스트

전체 테스트:

### Windows

```cmd
gradlew test --console=plain
```

### Linux / macOS / WSL

```bash
./gradlew test --console=plain
```

일반 테스트 환경에서는 H2 인메모리 DB를 사용하므로 운영 MySQL에 연결하지 않습니다.

기능 변경 또는 merge 전에는 다음을 확인합니다.

1. 전체 테스트 통과
2. secret 및 로컬 설정 파일 미커밋 확인
3. API 변경 시 관련 문서 갱신
4. 주요 사용자 흐름 직접 확인

### 실제 MySQL 교착 회귀 테스트

일반 테스트와 별도로 실제 MySQL의 잠금 경합과 교착 상황을 검증하는 테스트가 있습니다.

`MySqlProgressWithdrawalHttpAuditTests`는 회원 탈퇴와 프로젝트 상태 변경이 동시에 발생하는 상황에서 교착 재시도와 결과를 검증합니다.

이 테스트는 별도의 일회용 MySQL 데이터베이스에서 실행해야 합니다.

PowerShell 예시:

```powershell
$env:GOPICK_MYSQL_DEADLOCK_TEST = 'true'
$env:SPRING_DATASOURCE_URL = 'DB_URL'
$env:SPRING_DATASOURCE_USERNAME = 'DB_USER'
$env:SPRING_DATASOURCE_PASSWORD = 'DB_PASSWORD'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'

.\gradlew.bat test --tests '*MySqlProgressWithdrawalHttpAuditTests'
```

테스트 설정에서 스키마를 생성하고 삭제할 수 있으므로 운영 DB나 실제 데이터가 있는 DB에는 연결하지 않습니다.

---

## 6. EC2 배포 구성

프로젝트 운영 당시 백엔드는 AWS EC2에서 실행했습니다.

외부 요청은 Nginx가 받은 뒤 Spring Boot 애플리케이션으로 전달했습니다.

```text
Internet
   ↓
Nginx
   ↓
Spring Boot
```

Spring Boot 프로세스는 `gopick` systemd 서비스로 관리했습니다.

### 서비스 상태 확인

```bash
sudo systemctl status gopick --no-pager
```

### 시작

```bash
sudo systemctl start gopick
```

### 중지

```bash
sudo systemctl stop gopick
```

### 재시작

```bash
sudo systemctl restart gopick
```

### 로그 확인

```bash
sudo journalctl -u gopick -n 100 --no-pager
```

실시간 로그:

```bash
sudo journalctl -u gopick -f
```

### Nginx

상태 확인:

```bash
sudo systemctl status nginx --no-pager
```

설정 확인:

```bash
sudo nginx -t
```

설정이 정상이라면 재시작합니다.

```bash
sudo systemctl restart nginx
```

AWS EC2 staging 환경은 프로젝트 종료 후 종료했으므로 활성 staging 주소는 문서에 유지하지 않습니다.

---

## 7. Build

Spring Boot 실행 JAR 생성:

### Windows

```cmd
gradlew bootJar
```

### Linux / macOS / WSL

```bash
./gradlew bootJar
```

생성된 JAR은 기본적으로 다음 디렉터리에 위치합니다.

```text
build/libs/
```

운영 환경에서는 빌드된 Spring Boot 애플리케이션을 systemd 서비스로 실행했습니다.

---

## 8. DB Schema / Migration

현재 JPA 설정:

```properties
spring.jpa.hibernate.ddl-auto=update
```

새로운 테이블이나 단순한 컬럼 추가는 Hibernate가 반영할 수 있지만, 기존 제약조건 변경이나 기존 데이터가 있는 컬럼 변경은 `ddl-auto=update`에만 의존하지 않습니다.

수동으로 필요한 MySQL 변경은 `docs/migrations/`에서 SQL 파일로 관리합니다.

### project_member.profile_id nullable

파일:

```text
docs/migrations/issue-29-project-member-profile-nullable.sql
```

완료 프로젝트에서 탈퇴한 회원의 참여 기록을 유지하기 위해 `project_member.profile_id`를 nullable로 변경합니다.

```sql
ALTER TABLE project_member
MODIFY COLUMN profile_id BIGINT NULL;
```

### notification type / actor

파일:

```text
docs/migrations/notification-actor-and-event-types.sql
```

기존 `notification` 테이블에 다음 변경을 적용합니다.

- `type`을 `VARCHAR(40)`으로 변경
- `actor_account_id` nullable 컬럼 추가

### profile additional contacts

파일:

```text
docs/migrations/profile-additional-contacts.sql
```

기존 프로필 연락처 구조에 추가 연락처를 지원하기 위한 스키마 변경을 적용합니다.

---

## 9. 시간 데이터

GoPick의 주요 시간 값은 Java의 `LocalDateTime`을 사용합니다.

예:

- `account.created_at`
- `profile.created_at`
- `profile.updated_at`
- `project.created_at`
- `project.status_updated_at`
- `project.stage_updated_at`
- `project.deleted_at`
- `project_member.joined_at`
- `participation_request.created_at`
- `participation_request.resolved_at`
- `notification.created_at`
- `notification.read_at`

`LocalDateTime` 자체에는 timezone 정보가 포함되지 않으므로 서버의 JVM 시간대 설정에 영향을 받습니다.

운영 환경에서는 다음 JVM timezone을 사용했습니다.

```text
Asia/Seoul
```

---

## 10. Smoke Test

실제 프론트엔드와 백엔드 연동 시 최소 다음 흐름을 확인합니다.

### Account / Profile

- 회원가입
- 로그인 / 로그아웃
- Profile 생성 / 수정 / 상세
- 공개 Profile 검색

### Project

- Project 생성 / 수정 / 목록 / 상세
- 모집 조건 / 링크 저장
- AI 모집글 초안 생성
- 편집한 모집글 저장 / 재조회
- Project 시작
- Stage 변경
- Project 완료

### Team Flow

- APPLY 생성 / 취소
- OFFER 생성 / 취소
- 참여 요청 수락 / 거절
- 팀장 위임
- MEMBER 하차

### Discovery

- Profile 북마크 / 취소
- Project 스크랩 / 취소
- 프로젝트 기반 팀원 추천

### Notification

- 참여 요청 알림
- 수락 / 거절 알림
- 프로젝트 상태 변경 알림
- Stage 변경 알림
- 팀장 위임 / 하차 / 삭제 알림
- 읽음 처리
- 최초 읽음 시각 유지

문제가 발견되면 새 기능을 추가하기보다 기존 사용자 흐름의 정상 동작을 우선 확인합니다.