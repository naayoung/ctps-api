# CTPS Backend 구조 설명

이 문서는 `backend/`의 현재 코드 구조를 설명합니다. 전체 저장소 구조는 루트 [아키텍처 문서](../../docs/architecture.md)를 확인합니다.

## 도메인 구조

- `auth`: 로그인, 회원가입, 세션, CSRF, OAuth, 비밀번호 재설정, 회원 탈퇴
- `problem`: 문제 CRUD, 메타데이터, 풀이 이력, 외부 문제 검색, 프로그래머스 카탈로그
- `review`: 복습 상태, 복습 이력, 오늘의 복습, 복습 주기 정책
- `search`: 통합 검색, 검색 이벤트, 문제 상호작용 이벤트, 자주 찾는 유형
- `studyset`: Study Set 관리
- `dashboard`: 통계 집계
- `global`: 공통 응답, 예외, 보안 인터셉터, CORS/Web MVC 설정

## 인증 / 세션 구조

1. 로그인, 회원가입, OAuth callback에서 세션을 생성합니다.
2. 백엔드는 HTTP-only 세션 쿠키를 발급합니다.
3. CSRF 쿠키와 응답 헤더를 함께 내려줍니다.
4. `/api/**` 요청은 인증 인터셉터가 보호합니다.
5. POST/PUT/PATCH/DELETE 같은 unsafe method는 CSRF 인터셉터가 검사합니다.

인증 없이 접근 가능한 대표 경로:

- `/api/auth/login`
- `/api/auth/signup`
- `/api/auth/csrf`
- `/api/auth/username/recovery`
- `/api/auth/password/reset/request`
- `/api/auth/password/reset/confirm`
- `/api/auth/oauth/**`
- `/api/health`

## API 그룹

- `GET /api/health`
- `/api/auth`
- `/api/problems`
- `/api/reviews`
- `/api/dashboard`
- `/api/study-sets`
- `/api/search`
- `/api/external-problems`
- `/api/admin/external-search`

## 문제 / 복습 연결 구조

- 문제 생성/수정 시 `needsReview`와 풀이 흔적을 기준으로 복습 상태를 동기화합니다.
- 복습 완료 시 review history 엔트리를 저장합니다.
- 다음 복습일은 `ReviewSchedulePolicy` 구현체가 계산합니다.
- 복습 간격은 `REVIEW_SCHEDULE_INTERVALS_DAYS`로 조정할 수 있습니다.

## 검색 구조

1. 검색 요청을 정규화합니다.
2. 내부 문제 후보를 수집합니다.
3. 외부 provider 결과를 수집합니다.
4. 중복 후보를 정리합니다.
5. rule score와 provider normalized score를 조합합니다.
6. 최종 결과와 warning을 반환합니다.

## 외부 검색 provider

- solved.ac
- 프로그래머스 카탈로그
- LeetCode

일부 provider 실패는 전체 실패가 아니라 부분 성공과 warning으로 처리합니다.

## 운영 보호 구조

- 로그인, 검색, 아이디 찾기, 비밀번호 재설정에는 rate limit이 적용됩니다.
- 관리자 API는 `/api/admin/**` 별도 인터셉터로 보호합니다.
- CORS는 설정된 origin 또는 origin pattern 기준으로 허용합니다.
- cookie `SameSite`와 `Secure`는 프론트/백엔드 public URL 기준으로 계산합니다.
