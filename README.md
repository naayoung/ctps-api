# CTPS Backend

백엔드는 Spring Boot 기반 API 서버이며 세션 인증, 문제 관리, 복습, 통합 검색, 외부 검색 연동을 담당합니다.

## 실행

```bash
cd backend
./gradlew bootRun
```

필수 환경변수는 `src/main/resources/application.properties` 기준으로 주입합니다.

대표 예시:

```bash
DB_URL=jdbc:postgresql://localhost:5432/ctps
DB_USERNAME=postgres
DB_PASSWORD=postgres
APP_FRONTEND_BASE_URL=http://localhost:5173
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

배포 권장값:

```bash
APP_FRONTEND_BASE_URL=https://ctps.vercel.app
APP_BACKEND_BASE_URL=https://ctps.vercel.app
APP_CORS_ALLOWED_ORIGINS=https://ctps.vercel.app,https://ctps-web.vercel.app
APP_CORS_ALLOWED_ORIGIN_PATTERNS=
AUTH_SESSION_SECURE_COOKIE=true
AUTH_SESSION_SAME_SITE=Lax
AUTH_SESSION_COOKIE_DOMAIN=
```

Vercel 이 `/api/*` 를 Railway 로 프록시하는 구조라면 브라우저가 보는 API origin 은 프론트와 동일합니다.
이 경우 `APP_BACKEND_BASE_URL` 도 브라우저가 실제로 보는 public origin 기준으로 맞춰야 쿠키 정책이 `SameSite=Lax` 로 안정적으로 계산됩니다.

## 테스트

```bash
./gradlew test
```

## 외부 검색 운영

- 프로그래머스 카탈로그 수집 스크립트: `backend/scripts/`
- 관리자 적재 API: `/api/admin/external-search/programmers/ingest`
- 메트릭 API: `/api/admin/external-search/metrics`

검색 랭킹과 provider score 운영 기준은 루트 문서의 개발 구조 문서를 확인합니다.

- [개발 구조 문서](/Users/nayoung/workspace/side-project/ctps/docs/development-structure.md)

상세 문서는 루트 문서를 확인합니다.

- [프로젝트 개요](/Users/nayoung/workspace/side-project/ctps/docs/project-overview.md)
- [화면/기능 명세](/Users/nayoung/workspace/side-project/ctps/docs/feature-spec.md)
- [개발 구조 문서](/Users/nayoung/workspace/side-project/ctps/docs/development-structure.md)
- [개선 포인트 문서](/Users/nayoung/workspace/side-project/ctps/docs/improvement-backlog.md)
