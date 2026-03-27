# CTPS Backend

CTPS 백엔드는 세션 기반 인증과 문제/검색/복습 API를 제공하는 Spring Boot 서버입니다.  
현재 OAuth 로그인은 카카오, 구글, GitHub를 지원하며, 프론트 첫 진입 화면과 바로 연결되도록 구성되어 있습니다.

## OAuth 로그인 흐름

1. 프론트 첫 화면에서 소셜 로그인 버튼을 클릭합니다.
2. `GET /api/auth/oauth/{provider}/start`가 호출됩니다.
3. 백엔드는 OAuth `state`와 원래 이동하려던 해시 라우트를 쿠키에 저장한 뒤 provider 인증 화면으로 리다이렉트합니다.
4. provider callback이 `GET /api/auth/oauth/{provider}/callback`으로 돌아오면 사용자 정보를 조회합니다.
5. `oauth_accounts(provider, provider_user_id)` 기준으로 기존 계정을 찾고, 없으면 이메일 기준 기존 사용자 연결 또는 자동 가입을 수행합니다.
6. 로그인 성공 시 세션 쿠키와 CSRF 쿠키를 발급하고, 프론트 루트(`/`)로 복귀시킨 뒤 원래 화면으로 이동시킵니다.

## 필요한 환경변수

`.env.example` 파일을 참고해 아래 값을 준비합니다.

```bash
DB_URL=jdbc:postgresql://localhost:5432/ctps
DB_USERNAME=postgres
DB_PASSWORD=postgres

APP_FRONTEND_BASE_URL=http://localhost:5173
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173

AUTH_OAUTH_KAKAO_ENABLED=true
AUTH_OAUTH_KAKAO_CLIENT_ID=...
AUTH_OAUTH_KAKAO_CLIENT_SECRET=...
AUTH_OAUTH_KAKAO_REDIRECT_URI=http://localhost:8080/api/auth/oauth/kakao/callback

AUTH_OAUTH_GOOGLE_ENABLED=true
AUTH_OAUTH_GOOGLE_CLIENT_ID=...
AUTH_OAUTH_GOOGLE_CLIENT_SECRET=...
AUTH_OAUTH_GOOGLE_REDIRECT_URI=http://localhost:8080/api/auth/oauth/google/callback

AUTH_OAUTH_GITHUB_ENABLED=true
AUTH_OAUTH_GITHUB_CLIENT_ID=...
AUTH_OAUTH_GITHUB_CLIENT_SECRET=...
AUTH_OAUTH_GITHUB_REDIRECT_URI=http://localhost:8080/api/auth/oauth/github/callback
```

## 로컬 실행

Spring Boot는 `.env`를 자동 로드하지 않으므로, 쉘에 값을 올린 뒤 실행합니다.

```bash
cd backend
cp .env.example .env
set -a
source .env
set +a
./gradlew bootRun
```

## OAuth Provider 콘솔 설정

### 카카오 개발자센터

- 웹 플랫폼에 `http://localhost:5173` 등록
- Redirect URI에 `http://localhost:8080/api/auth/oauth/kakao/callback` 등록
- 이메일과 프로필 제공 동의 설정

### 구글 콘솔

- OAuth Client를 Web application으로 생성
- Authorized JavaScript origins에 `http://localhost:5173` 등록
- Authorized redirect URIs에 `http://localhost:8080/api/auth/oauth/google/callback` 등록

### GitHub OAuth App

- Homepage URL에 `http://localhost:5173` 등록
- Authorization callback URL에 `http://localhost:8080/api/auth/oauth/github/callback` 등록
- 이메일 조회를 위해 `user:email` scope 사용
