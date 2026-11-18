# GoPick Operations

로컬 개발, 테스트, 환경변수, Staging 배포 확인, 기존 MySQL 마이그레이션을 정리한 문서입니다.

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

테스트는 별도 H2 인메모리 DB를 사용합니다.

---

## 2. 환경변수

`src/main/resources/application.properties` 기준입니다.

| 환경변수 | 필수 | 기본값 / 용도 |
|---|---|---|
| `PORT` | 선택 | HTTP 포트, 기본값 `8080` |
| `DB_URL` | 선택 | JDBC URL, 기본값 `jdbc:mysql://localhost:3306/gopick` |
| `DB_USERNAME` | 선택 | MySQL 사용자, 기본값 `root` |
| `DB_PASSWORD` | 로컬 / 서버 DB 연결 시 필수 | MySQL 사용자 비밀번호 |
| `CORS_ALLOWED_ORIGINS` | 선택 | `http://localhost:5173,http://127.0.0.1:5173` |
| `SESSION_COOKIE_SAME_SITE` | 선택 | `lax` |
| `SESSION_COOKIE_SECURE` | 선택 | `false` |
| `RECOMMENDATION_AI_MODE` | 선택 | `stub`; 실제 Upstage 추천은 `upstage` |
| `UPSTAGE_API_KEY` | AI 기능 사용 시 필수 | Upstage API key |
| `UPSTAGE_BASE_URL` | 선택 | `https://api.upstage.ai/v1` |
| `UPSTAGE_MODEL` | 선택 | `solar-pro4` |

API key, DB 비밀번호 등 secret은 Git에 커밋하지 않습니다.

### Railway

루트의 Dockerfile은 Java 21로 애플리케이션을 빌드하고 실행합니다. Railway 앱 서비스에서 MySQL 서비스의 **내부 연결 정보**를 참조하는 환경변수를 설정합니다. 아래 `MySQL`은 Railway에서 실제로 만든 DB 서비스 이름으로 바꿉니다.

```text
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
CORS_ALLOWED_ORIGINS=https://실제-프론트-주소
```

Railway의 `PORT`는 앱에서 자동으로 사용합니다. 프론트와 백엔드가 서로 다른 사이트이고 세션 쿠키가 필요하면 HTTPS를 사용하고 `SESSION_COOKIE_SAME_SITE=none`, `SESSION_COOKIE_SECURE=true`를 설정합니다. 브라우저에서는 `credentials: 'include'`를 사용합니다. 프론트 주소가 여러 개이면 CORS 허용 주소를 쉼표로 구분합니다.

AI 모집글은 `UPSTAGE_API_KEY`가 필요합니다. 실제 AI 추천도 사용하려면 같은 키와 `RECOMMENDATION_AI_MODE=upstage`를 설정합니다. 비밀값은 Railway의 변수로 관리하고 저장소에 넣지 않습니다.

기존 staging 데이터를 옮길 때는 전환 전 백업을 만들고 원본과 대상 DB 및 버전을 확인합니다. 새 DB에 복원한 뒤 아래 마이그레이션 적용 여부와 주요 기능을 검증합니다. 전환 직전 기존 서버의 쓰기를 중지하고 마지막 데이터 복사와 검증을 마친 다음 프론트의 API 주소를 변경합니다. 복사 이후 두 DB는 자동으로 동기화되지 않습니다.

---

## 3. 로컬 실행

Windows CMD 예시:

```cmd
set DB_PASSWORD=...
set UPSTAGE_API_KEY=...
set RECOMMENDATION_AI_MODE=upstage
gradlew bootRun
```

WSL / Linux 예시:

```bash
export DB_PASSWORD='...'
export UPSTAGE_API_KEY='...'
export RECOMMENDATION_AI_MODE='upstage'
./gradlew bootRun
```

AI 추천을 실제 외부 API 없이 확인하려면 기본 `stub` 모드를 사용할 수 있습니다.

---

## 4. 테스트

전체 테스트:

Windows:

```cmd
gradlew test --console=plain
```

WSL / Linux:

```bash
./gradlew test --console=plain
```

테스트용 설정은 H2를 사용하므로 운영 MySQL에 연결하지 않습니다.

최종 merge 전 최소 기준:

1. 전체 테스트 통과
2. secret / 로컬 설정 파일 미커밋 확인
3. 변경 API와 문서 동기화
4. Staging 주요 흐름 smoke test

---

## 5. Staging

Base URL:

```text
https://3-39-65-219.nip.io
```

현재 서비스는 세션 기반 인증이므로 curl 테스트 시 cookie jar를 사용합니다.

회원가입 예시:

```bash
curl -i -c cookies.txt \
  -X POST "https://3-39-65-219.nip.io/accounts" \
  -H "Content-Type: application/json" \
  -d '{"email":"stage-test@example.com","password":"test1234"}'
```

회원가입 성공 시 자동 로그인되므로 이후:

```bash
curl -i -b cookies.txt "https://3-39-65-219.nip.io/me"
```

처럼 같은 cookie를 사용합니다.

---

## 6. 서버 배포 확인

현재 서버 운영 흐름에서 배포 스크립트를 사용하는 경우:

```bash
cd ~/gopick-server
./deploy.sh
```

배포 후 서비스 확인:

```bash
sudo systemctl status gopick --no-pager
sudo journalctl -u gopick -n 100 --no-pager
```

Staging의 Upstage 기능을 사용하려면 서버 프로세스 환경에 최소 다음 값이 필요합니다.

```text
UPSTAGE_API_KEY
RECOMMENDATION_AI_MODE=upstage
```

`UPSTAGE_BASE_URL`, `UPSTAGE_MODEL`은 application 기본값을 사용할 수 있습니다.

---

## 7. DB Schema / Migration

현재 JPA 설정:

```properties
spring.jpa.hibernate.ddl-auto=update
```

새 테이블 / 컬럼의 일반적인 추가는 Hibernate가 처리할 수 있지만, 기존 제약 변경이나 enum 컬럼 변경을 `ddl-auto=update`에만 의존하면 안 됩니다.

기존 MySQL에 필요한 수동 migration SQL은 `docs/migrations/`에 유지합니다.

### project_member.profile_id nullable

파일:

```text
docs/migrations/issue-29-project-member-profile-nullable.sql
```

완료 프로젝트에서 탈퇴한 회원의 참여 기록을 남기기 위해 기존 DB의 `project_member.profile_id`를 nullable로 바꿉니다.

```sql
ALTER TABLE project_member MODIFY COLUMN profile_id BIGINT NULL;
```

기존 DB에서 아직 적용하지 않았다면 관련 서버 버전을 배포하기 전에 한 번 실행합니다.

### notification type / actor

파일:

```text
docs/migrations/notification-actor-and-event-types.sql
```

기존 `notification` 테이블이 있고 migration이 적용되지 않은 경우:

- `type`을 `VARCHAR(40)`으로 변경
- `actor_account_id` 추가

새 DB에 notification 테이블이 없다면 Hibernate가 현재 스키마를 생성합니다.

여러 앱 인스턴스가 같은 DB를 사용한다면 새 NotificationType을 기록하기 전에 이전 버전 인스턴스를 중지하거나 교체합니다.

---

## 8. 최종 Smoke Test

제출 전 실제 프론트 + Staging에서 최소 다음 흐름을 확인합니다.

### Account / Profile

- 회원가입
- 로그인 / 로그아웃
- Profile 생성 / 수정 / 상세
- 공개 Profile 검색

### Project

- Project 생성 / 수정 / 목록 / 상세
- 모집 조건 / 링크 저장
- AI 모집글 초안 생성
- 편집한 `post` 저장 / 재조회
- Project 시작
- Stage 변경
- 완료

### Team flow

- APPLY 생성 / 취소
- OFFER 생성 / 취소
- 수락 / 거절
- 팀장 위임
- MEMBER 하차

### Discovery

- Profile 북마크 / 취소
- Project 스크랩 / 취소
- 프로젝트 기반 팀원 추천

### Notification

- 참여 요청 알림
- 수락 / 거절 알림
- 프로젝트 상태 / Stage / 위임 / 하차 관련 알림
- 읽음 처리

문제가 발견되면 새 기능을 추가하기보다 제출 범위의 기존 사용자 흐름을 우선 수정합니다.
