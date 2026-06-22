# CTPS Backend 배포 메모

이 문서는 `backend/` 단독 배포 기준 문서입니다. 전체 배포 구조는 루트 [배포 문서](../../docs/deployment.md)를 함께 확인합니다.

## 실행 방식

백엔드는 Spring Boot JAR 또는 container 이미지로 실행할 수 있습니다.

```bash
./gradlew build
java -jar build/libs/ctps-api-0.0.1-SNAPSHOT.jar
```

컨테이너가 필요하면 `backend/Dockerfile`을 기준으로 빌드합니다.

```bash
docker build -t ctps-api .
```

## 기본 포트와 health check

- 기본 포트: `8080`
- health endpoint:
  - `/`
  - `/health`
  - `/api/health`

## 필수 환경 변수

### DB

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

### Flyway / JPA

- `SPRING_FLYWAY_ENABLED`
- `SPRING_FLYWAY_BASELINE_ON_MIGRATE`
- `SPRING_FLYWAY_BASELINE_VERSION`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`

### 프론트 연결 / 쿠키 / CORS

- `APP_FRONTEND_BASE_URL`
- `APP_BACKEND_BASE_URL`
- `APP_CORS_ALLOWED_ORIGINS`
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS`
- `AUTH_SESSION_SECURE_COOKIE`
- `AUTH_SESSION_SAME_SITE`
- `AUTH_SESSION_COOKIE_DOMAIN`

### 인증 / OAuth

- `AUTH_BOOTSTRAP_ENABLED`
- `AUTH_OAUTH_KAKAO_*`
- `AUTH_OAUTH_GOOGLE_*`
- `AUTH_OAUTH_GITHUB_*`

### 외부 검색 / 운영

- `PROGRAMMERS_FEED_URL`
- `PROGRAMMERS_IMPORT_DIR`
- `EXTERNAL_SEARCH_*`
- `ADMIN_SECURITY_TOKEN`
- `ADMIN_ALLOWED_IPS`

전체 목록과 용도는 `backend/.env.example`에 정리되어 있습니다.

## 권장 운영 구조

- 프론트는 Vercel에서 정적 배포합니다.
- 백엔드는 Railway 또는 JVM/container 환경에서 실행합니다.
- 브라우저는 `/api`로 요청하고, Vercel rewrite가 백엔드 `/api/*`로 전달합니다.

이 구조에서는 브라우저 기준 API origin이 프론트와 같아져 세션 쿠키 정책을 단순하게 유지할 수 있습니다.

## DB / Flyway 주의사항

- 새 DB는 `SPRING_FLYWAY_ENABLED=true`, `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`로 시작합니다.
- 기존 운영 DB가 Flyway 없이 만들어졌다면 [Flyway Baseline 전환 문서](./FLYWAY_BASELINE_TRANSITION.md)를 먼저 따릅니다.
- baseline 전환 후에는 Hibernate auto-update를 다시 켜지 않습니다.

## 관리자 API

`/api/admin/**` API는 관리자 토큰과 허용 IP 설정으로 보호합니다.

- token 값은 환경 변수 또는 hosting secret storage에만 저장합니다.
- GitHub Actions에서 카탈로그 적재를 호출할 경우 repository secret으로 주입합니다.
- 공개 문서나 `.env.example`에는 실제 값처럼 보이는 예시를 남기지 않습니다.
