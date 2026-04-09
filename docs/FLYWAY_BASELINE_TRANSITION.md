# Flyway Baseline 전환 문서

이 문서는 기존 운영 DB를 Flyway 체계로 전환할 때만 필요한 1회성 운영 절차입니다.
새로 만드는 빈 DB 환경에는 이 문서가 아니라 일반 Flyway 실행 설정을 사용하면 됩니다.

## 왜 이 프로젝트가 단일 baseline 을 쓰는가

이 서비스는 과거에 Flyway 마이그레이션이 아니라 Hibernate schema auto-update 기반으로 생성된 Railway PostgreSQL DB를 사용해 운영되었습니다.

운영 환경에 신뢰할 수 있는 Flyway 이력이 없기 때문에, 가장 안전한 전환 전략은 아래와 같습니다.

1. Freeze the current schema as a single baseline migration.
2. Mark the existing production schema as already being at that baseline.
3. Start creating only forward migrations after that point.

그래서 현재 마이그레이션 히스토리는 `src/main/resources/db/migration/V1__init.sql`을 기준 baseline 으로 두고, 이후 변경분을 `V2`, `V3` ... 형태로 이어가도록 정리되어 있습니다.

## 언제 이 문서가 필요한가

이 문서가 필요한 경우:

- 이미 운영 중인 PostgreSQL DB가 있음
- 그 DB가 과거 Hibernate schema auto-update 기반으로 만들어졌음
- `flyway_schema_history`가 아직 없음

이 문서가 필요 없는 경우:

- 새로 만드는 빈 DB 환경
- 이미 Flyway 기반으로 관리 중인 환경

## 권장 전환 절차

### 1. 먼저 Flyway 없이 현재 스키마 검증

설정:

- `SPRING_FLYWAY_ENABLED=false`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`

이 단계는 Hibernate 가 추가 변경을 하지 않는 상태에서, 현재 운영 스키마로 애플리케이션이 정상 부팅되는지 먼저 확인하는 절차입니다.

### 2. 기존 운영 DB에 baseline 메타데이터 생성

아래 설정으로 1회성 배포를 진행합니다:

- `SPRING_FLYWAY_ENABLED=true`
- `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`
- `SPRING_FLYWAY_BASELINE_VERSION=1`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`

운영 DB는 이미 비어 있지 않으므로, Flyway 는 `flyway_schema_history`를 만들고 버전 `1`을 baseline 으로 기록한 뒤 `V1__init.sql` 실행은 건너뜁니다.

이후 저장소에 `V1` 뒤 버전이 있으면 같은 실행에서 이어서 적용합니다.
예를 들어 현재 저장소에는 `V2__add_oauth_accounts.sql` 이후 마이그레이션이 있으므로, baseline 등록 후 다음 버전들이 순서대로 적용됩니다.

### 3. 이후부터는 일반 Flyway 시작 방식 사용

baseline 메타데이터가 만들어진 뒤에는 아래 일반 설정으로 전환합니다:

- `SPRING_FLYWAY_ENABLED=true`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
- `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`

이후부터는 애플리케이션 시작 시 기존 스키마를 검증하고, Flyway 는 `V1__init.sql`이 이미 적용된 것으로 간주합니다.

## 새 환경에서는 어떻게 하나

새로 만드는 빈 DB 환경에서는 아래처럼 사용합니다:

1. Set `SPRING_FLYWAY_ENABLED=true`
2. Set `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
3. Leave `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`

이 경우 Flyway 가 `V1__init.sql`부터 실행해서 스키마를 처음부터 생성합니다.

## 중요 주의사항

- 빈 DB에 `baselineOnMigrate=true`를 사용하면 안 됩니다. 그렇게 하면 `V1__init.sql`이 건너뛰어집니다.
- 전환 이후에는 Hibernate auto-update를 다시 켜지 않는 편이 안전합니다. 이후 스키마 변경은 `V2__...`, `V3__...` 같은 마이그레이션으로 추가해야 합니다.
- 어떤 환경에 이미 `flyway_schema_history`가 존재한다면 바로 진행하지 말고 먼저 상태를 점검해야 합니다. 필요하면 `flyway repair` 또는 수동 정리가 선행되어야 합니다.
