# CTPS Backend

CTPS 백엔드는 세션 기반 인증과 문제/검색/복습 API를 제공하는 Spring Boot 서버입니다.  
현재 인증은 일반 회원가입/로그인(local)과 카카오, 구글, GitHub OAuth 로그인을 함께 지원하며, 프론트 첫 진입 화면과 바로 연결되도록 구성되어 있습니다.

## 인증 구조 요약

- `users` 테이블이 local/social 공통 사용자 엔티티 역할을 합니다.
- `oauth_accounts`는 social provider 연결 정보를 저장합니다.
- local 계정은 이메일 + 비밀번호로 로그인합니다.
- social 계정은 `oauth_accounts(provider, provider_user_id)`로 식별합니다.
- social 로그인 시 같은 이메일의 active local 계정이 이미 있으면 해당 사용자에 provider를 연결합니다.
- 회원 탈퇴는 soft delete로 처리하고, 기존 문제/복습 데이터 참조는 유지합니다.
- 탈퇴 시 세션과 OAuth 연결을 제거하고 사용자 이메일/username을 익명화하므로 같은 이메일 또는 같은 social 계정으로 다시 가입할 수 있습니다.

## 일반 회원가입 / 로그인 흐름

1. 프론트 첫 화면에서 회원가입 또는 로그인 폼을 선택합니다.
2. 회원가입은 `POST /api/auth/signup`으로 이메일, 표시 이름, 비밀번호를 전송합니다.
3. 백엔드는 이메일 중복 여부, 비밀번호 규칙, 비밀번호 확인 일치를 검증합니다.
4. 가입 성공 시 local 사용자 계정을 만들고 바로 세션을 발급합니다.
5. 일반 로그인은 `POST /api/auth/login`으로 이메일과 비밀번호를 검증한 뒤 세션을 발급합니다.

가입 직후 자동 로그인 방식을 사용하는 이유는, 첫 진입 인증 화면에서 바로 CTPS 실제 기능 화면으로 자연스럽게 이어지게 하기 위해서입니다.

## OAuth 로그인 흐름

1. 프론트 첫 화면에서 소셜 로그인 버튼을 클릭합니다.
2. `GET /api/auth/oauth/{provider}/start`가 호출됩니다.
3. 백엔드는 OAuth `state`와 원래 이동하려던 해시 라우트를 쿠키에 저장한 뒤 provider 인증 화면으로 리다이렉트합니다.
4. provider callback이 `GET /api/auth/oauth/{provider}/callback`으로 돌아오면 사용자 정보를 조회합니다.
5. `oauth_accounts(provider, provider_user_id)` 기준으로 기존 계정을 찾고, 없으면 이메일 기준 기존 사용자 연결 또는 자동 가입을 수행합니다.
6. 로그인 성공 시 세션 쿠키와 CSRF 쿠키를 발급하고, 프론트 루트(`/`)로 복귀시킨 뒤 원래 화면으로 이동시킵니다.

## 계정 관리 API

- `POST /api/auth/password`
  - local 계정만 사용 가능
  - 현재 비밀번호 확인 후 새 비밀번호로 변경
- `POST /api/auth/withdraw`
  - 확인 문구 `탈퇴합니다` 필요
  - local 계정은 현재 비밀번호도 함께 확인
  - 탈퇴 시 세션 무효화 + OAuth 연결 제거 + soft delete 처리

social 계정의 비밀번호 변경을 막은 이유는, 현재 구조에서 social-only 계정은 외부 provider가 인증 주체이고 CTPS 내부 비밀번호 로그인 경로를 갖지 않기 때문입니다.

## 필요한 환경변수

`.env.example` 파일을 참고해 아래 값을 준비합니다.

```bash
DB_URL=jdbc:postgresql://localhost:5432/ctps
DB_USERNAME=postgres
DB_PASSWORD=postgres

APP_FRONTEND_BASE_URL=http://localhost:5173
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173

AUTH_BOOTSTRAP_ENABLED=true
AUTH_BOOTSTRAP_EMAIL=ctps@local.ctps

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

## 프로그래머스 카탈로그 수집

프로그래머스 외부 검색은 실시간 크롤링이 아니라, 수집 스크립트가 만든 JSON 스냅샷을 `PROGRAMMERS_IMPORT_DIR`에 저장하고 백엔드가 이를 적재하는 방식입니다.

1. `PROGRAMMERS_IMPORT_DIR`, `ADMIN_SECURITY_TOKEN`, `CTPS_PROGRAMMERS_INGEST_URL`를 설정합니다.
2. `backend/scripts`에서 의존성을 설치합니다.
3. Playwright 수집 스크립트를 실행합니다.

```bash
cd backend/scripts
npm install
node programmers_catalog_worker.mjs --pages 1 --dry-run
node programmers_catalog_worker.mjs --pages 3
node programmers_catalog_worker.mjs --pages 3 --full-refresh
```

기본 모드는 `incremental`이며, 최신 스냅샷에 이미 있는 `externalId`는 다시 상세 수집하지 않고 신규 문제만 추가합니다. `--full-refresh` 또는 `PROGRAMMERS_CRAWLER_MODE=full-refresh`를 사용하면 전체 재수집으로 동작합니다.

`--dry-run`은 샘플 JSON만 출력하고 파일을 쓰지 않습니다. 일반 실행은 최신 스냅샷 JSON을 생성하고, `CTPS_PROGRAMMERS_INGEST_URL`이 설정돼 있으면 적재 API까지 연달아 호출합니다.

운영에서는 하루 1회 스케줄러에서 아래 명령만 실행하면 됩니다. 주기 변경은 스케줄러 cron만 바꾸면 되고, 동작 방식 변경은 `PROGRAMMERS_CRAWLER_MODE`만 조정하면 됩니다.

```bash
cd backend/scripts
npm install
node programmers_catalog_worker.mjs --pages 3
```

### GitHub Actions 운영 예시

바로 사용할 수 있는 워크플로우는 [programmers-catalog-sync.yml](/Users/nayoung/workspace/side-project/ctps/backend/.github/workflows/programmers-catalog-sync.yml) 에 추가되어 있습니다.

- 매일 1회: `incremental`
- 매월 1회: `full-refresh`
- 필요하면 `workflow_dispatch`로 수동 실행 가능

워크플로우는 수집 결과를 `generated/programmers-catalog/latest.json` 으로 커밋합니다. 배포된 backend에는 아래처럼 GitHub raw URL을 `PROGRAMMERS_FEED_URL` 로 연결하면 됩니다.

```bash
PROGRAMMERS_FEED_URL=https://raw.githubusercontent.com/<owner>/<repo>/main/generated/programmers-catalog/latest.json
```

워크플로우에서 바로 적재 API까지 호출하려면 GitHub Actions Secrets에 아래 값을 넣습니다.

```bash
CTPS_PROGRAMMERS_INGEST_URL=https://<your-backend>/api/admin/external-search/programmers/ingest
ADMIN_SECURITY_TOKEN=<your-admin-token>
```

워크플로우 주기를 바꾸고 싶다면 `.github/workflows/programmers-catalog-sync.yml` 의 `schedule.cron` 만 수정하면 됩니다. 수집 모드는 cron과 분리되어 있어서, 일일은 `incremental`, 월간은 `full-refresh` 정책을 쉽게 유지할 수 있습니다.

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

## 탈퇴와 OAuth 연동 해제의 차이

- CTPS 회원 탈퇴: CTPS 서비스 내부 계정 종료
- OAuth 연동 해제: 카카오, 구글, GitHub에서 해당 앱 권한을 별도로 제거하는 작업

즉, CTPS에서 탈퇴해도 provider 쪽 앱 연결은 남아 있을 수 있으므로 필요하면 각 provider 콘솔에서 추가로 해제해야 합니다.
