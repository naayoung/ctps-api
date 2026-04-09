# CTPS Backend 구조 설명

## 도메인 구조

- `auth`: 로그인, 세션, OAuth, 비밀번호 재설정
- `problem`: 문제 CRUD, 메타데이터, 풀이 이력, 외부 문제
- `review`: 복습 상태, 복습 이력, 오늘의 복습
- `search`: 통합 검색, 검색 이벤트, 자주 찾는 유형
- `studyset`: Study Set 관리
- `dashboard`: 통계 집계

## 인증 / 세션 구조

1. 로그인 또는 회원가입 시 세션 생성
2. 세션 쿠키 발급
3. CSRF 쿠키와 응답 헤더 설정
4. `/api/**` 요청은 인증 인터셉터가 보호
5. unsafe method는 CSRF 인터셉터가 검사

예외 경로:

- `/api/auth/login`
- `/api/auth/signup`
- `/api/auth/csrf`
- `/api/auth/username/recovery`
- `/api/auth/password/reset/request`
- `/api/auth/password/reset/confirm`
- `/api/auth/oauth/**`
- `/api/health`

## 문제 / 복습 연결 구조

- 문제 생성 / 수정 시 `needsReview`와 풀이 흔적을 기준으로 복습 큐 동기화
- 복습 완료 시 review history 엔트리 저장
- 다음 복습일은 `ReviewSchedulePolicy`를 통해 계산

## 검색 구조

1. 검색 요청 정규화
2. 내부 문제 후보 수집
3. 외부 provider 검색
4. deduplication
5. rule score + provider normalized score 계산
6. 최종 결과 조합

## 외부 검색 provider

- solved.ac
- 프로그래머스 카탈로그
- LeetCode

일부 provider 실패는 전체 실패가 아니라 부분 성공과 warning으로 처리합니다.

## 운영 보호 구조

- 로그인 / 검색 / 비밀번호 재설정에 rate limit 적용
- 관리자 API는 `/api/admin/**` 별도 보호
- CORS는 configured origin 기준
- cookie sameSite / secure는 frontend/backend base URL 기준으로 계산
