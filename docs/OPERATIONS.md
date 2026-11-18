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

루트의 Dockerfile은 Java 21로 애플리케이션을 빌드하고 실행합니다. Railway 앱 서비스의 배포 소스로 `gopick-server`의 `main`을 연결합니다. 저장소가 목록에 없으면 Railway GitHub App의 저장소 접근 범위를 확인합니다. 앱 서비스에서 MySQL 서비스의 **내부 연결 정보**를 참조하도록 환경변수를 설정합니다. 아래 `MySQL`은 Railway에서 실제로 만든 DB 서비스 이름으로 바꿉니다.

```text
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
CORS_ALLOWED_ORIGINS=https://실제-프론트-주소
```

Railway의 `PORT`는 앱이 자동으로 사용합니다. 앱 서비스의 **Networking → Public Networking → Generate Domain**에서 공개 HTTPS 주소를 생성하고 앱이 듣는 포트로 연결된 것을 확인합니다. 프론트의 직접 호출 주소나 프록시 대상에는 이 공개 주소를 사용합니다. MySQL 연결은 서비스 내부 주소를 사용합니다.

AI 모집글은 `UPSTAGE_API_KEY`가 필요합니다. 실제 AI 추천도 사용하려면 같은 키와 `RECOMMENDATION_AI_MODE=upstage`를 설정합니다. 비밀값은 Railway의 변수로 관리합니다.

기존 AWS staging에서 전환하는 순서입니다.

1. 원본·대상 DB와 배포 커밋을 확인합니다. 최종 복사 전에 AWS staging의 쓰기를 중지한 뒤 백업을 만들어 Railway MySQL에 복원합니다.
2. **최종 복원된 DB에서** 이 문서 7절의 두 스키마 변경 적용 여부를 확인합니다. 기존 테이블에 변경이 빠져 있으면 해당 SQL을 적용합니다. 아직 새 알림 유형을 읽을 수 없는 구버전 앱이 같은 DB에 연결되어 있으면 이 단계에서 교체합니다.
3. 새 앱을 시작하고 로그와 공개 도메인 응답을 확인합니다. 인증 없이 `GET /me`가 `401 UNAUTHENTICATED`를 반환하는지 확인합니다. 이어서 Railway 공개 주소를 직접 호출하거나 그 주소로 프록시하는 별도 테스트 프론트에서 로그인하고, 기존 계정의 프로젝트·요청·알림 조회 등 8절의 주요 흐름을 테스트합니다.
4. 검증을 마친 뒤 프론트 API 주소를 Railway로 전환합니다. 두 DB는 복사 후 자동으로 동기화되지 않습니다.

### 프론트 연동

로컬의 `gopick-web`에서 `npm run dev`를 실행하면 [.env.development](https://github.com/seonyoung05/gopick-web/blob/main/.env.development)의 `VITE_API_BASE_URL=/api`를 사용합니다. [Vite 개발 프록시](https://github.com/seonyoung05/gopick-web/blob/main/vite.config.ts)는 `/api`를 제거하고 요청을 AWS staging에 전달합니다. 브라우저에서는 각자의 컴퓨터에서 dev 서버가 출력한 주소를 엽니다.

정적 빌드 배포에서는 호스팅에 맞는 방식을 설정합니다.

| 방식 | 프론트 빌드·호스팅 설정 |
|---|---|
| 같은 출처의 `/api` | `VITE_API_BASE_URL=/api`로 빌드하고 정적 호스트에 `/api/*`를 백엔드로 전달하는 리버스 프록시를 설정합니다. 백엔드 경로로 전달할 때 `/api` 접두어를 제거하고 `Cookie` 및 `Set-Cookie`를 전달합니다. Spring이 전달받은 브라우저 `Origin`을 교차 출처로 판단하면 `CORS_ALLOWED_ORIGINS`에 그 프론트 출처를 등록합니다. |
| 백엔드 공개 주소 직접 호출 | 빌드 환경에 `VITE_API_BASE_URL=https://백엔드-공개-주소`를 설정하고 `npm run build`로 다시 빌드합니다. 백엔드 `CORS_ALLOWED_ORIGINS`에 실제 프론트의 출처를 등록합니다. 여러 출처는 쉼표로 구분합니다. |

브라우저에서 인증에 필요한 요청은 `credentials: 'include'`로 보냅니다. 브라우저에서 백엔드를 직접 호출하며 두 사이트가 다르면 HTTPS를 사용하고 백엔드에서 `SESSION_COOKIE_SAME_SITE=none`과 `SESSION_COOKIE_SECURE=true`를 설정합니다. 브라우저가 사이트 간 쿠키를 막는 환경에서는 같은 출처의 `/api` 방식을 사용합니다.

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

AWS staging 주소

```text
https://3-39-65-219.nip.io
```

스테이징 주소와 배포 커밋을 확인한 뒤 테스트합니다. `GET /me`의 비로그인 `401`은 서비스 응답을 확인하는 데 쓰고, 최신 API의 동작은 해당 경로에서 별도로 확인합니다. 세션을 유지하려면 curl의 cookie jar를 사용합니다.

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

기존 `notification` 테이블이 있고 이 변경이 빠져 있으면, 대상 DB에 서버를 배포하기 전에 [마이그레이션 SQL](migrations/notification-actor-and-event-types.sql)을 한 번 실행합니다.

- `type`을 `VARCHAR(40)`으로 변경
- `actor_account_id`를 nullable 컬럼으로 추가

`notification` 테이블이 없는 DB에서는 새 앱의 Hibernate가 현재 스키마를 생성합니다. 여러 앱 인스턴스가 같은 DB를 사용한다면 새 알림 유형을 기록하기 전에 이전 버전 인스턴스를 중지하거나 교체합니다.

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
- 프로젝트 상태 / Stage / 위임 / 하차·삭제 관련 알림
- 참여 요청 알림에서 해당 보낸·받은 요청으로 이동
- 읽음 처리와 재조회 시 최초 읽음 시각 유지

문제가 발견되면 새 기능을 추가하기보다 제출 범위의 기존 사용자 흐름을 우선 수정합니다.
