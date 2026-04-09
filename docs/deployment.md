# CTPS Backend 배포 메모

## 실행 방식

- Spring Boot JAR 실행
- Docker 이미지 실행

Docker 이미지는 저장소의 [Dockerfile](/Users/nayoung/workspace/side-project/ctps/backend/Dockerfile) 기준으로 빌드합니다.

## 기본 포트

- `8080`

## 핵심 환경변수

### DB

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

### 프론트 연결 / 쿠키 / CORS

- `APP_FRONTEND_BASE_URL`
- `APP_BACKEND_BASE_URL`
- `APP_CORS_ALLOWED_ORIGINS`
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS`
- `AUTH_SESSION_SECURE_COOKIE`
- `AUTH_SESSION_SAME_SITE`
- `AUTH_SESSION_COOKIE_DOMAIN`

### OAuth

- `AUTH_OAUTH_KAKAO_*`
- `AUTH_OAUTH_GOOGLE_*`
- `AUTH_OAUTH_GITHUB_*`

### 외부 검색 / 운영

- `PROGRAMMERS_FEED_URL`
- `PROGRAMMERS_IMPORT_DIR`
- `EXTERNAL_SEARCH_*`
- `ADMIN_SECURITY_TOKEN`
- `ADMIN_ALLOWED_IPS`

## 배포 구조

권장 구조:

- 프론트: Vercel
- 백엔드: Railway 또는 컨테이너 환경
- 브라우저는 `/api`로 호출
- Vercel rewrite가 backend `/api/*`로 전달

## 운영 시 주의사항

- 세션 인증 구조라 CORS와 cookie sameSite / secure 설정이 핵심입니다.
- 프론트가 보는 public origin 기준으로 `APP_BACKEND_BASE_URL`을 맞추는 편이 안정적입니다.
- 기존 운영 DB는 Flyway baseline 절차 후 운영해야 합니다.
