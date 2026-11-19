# MySQL 프로젝트 상태 변경 교착 회귀 테스트

`MySqlProgressWithdrawalHttpAuditTests`는 실제 MySQL 교착을 유도하고 회원 탈퇴와 동시에 프로젝트 시작·완료·단계 변경 요청이 자동 재시도되는지 확인합니다. 첫 요청이 성공하고 살아 있는 팀원에게 알림이 한 건만 남는지도 검사합니다.

기본 H2 테스트에서는 실행되지 않습니다. 테스트 설정의 `spring.jpa.hibernate.ddl-auto=create-drop`이 스키마를 생성하고 종료할 때 삭제하므로 **운영 DB나 다른 데이터가 있는 DB에 절대 연결하지 말고**, 별도의 일회용 MySQL 8.4 데이터베이스를 사용하세요. 테스트 계정은 스키마 생성·삭제와 `performance_schema.data_lock_waits`, `performance_schema.threads`, `information_schema.innodb_trx` 조회 권한이 필요합니다.

PowerShell 실행 예시 (`DB_URL`, `DB_USER`, `DB_PASSWORD`는 테스트 DB 값으로 교체):

```powershell
$env:GOPICK_MYSQL_DEADLOCK_TEST = 'true'
$env:SPRING_DATASOURCE_URL = 'DB_URL'
$env:SPRING_DATASOURCE_USERNAME = 'DB_USER'
$env:SPRING_DATASOURCE_PASSWORD = 'DB_PASSWORD'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
.\gradlew.bat test --tests '*MySqlProgressWithdrawalHttpAuditTests'
```

이 테스트는 실제 잠금 경합과 트랜잭션 가중치를 사용하므로 DB 버전이나 설정에 따라 교착 희생자가 달라질 수 있습니다. 희생자가 기대와 다르면 테스트 실패 원인을 확인해야 합니다.
