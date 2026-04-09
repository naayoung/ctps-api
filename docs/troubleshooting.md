# CTPS Backend 이슈 / 트러블슈팅

## 로그인은 되는데 세션 유지가 안 되는 경우

확인 항목:

- `APP_FRONTEND_BASE_URL`
- `APP_BACKEND_BASE_URL`
- `APP_CORS_ALLOWED_ORIGINS`
- `AUTH_SESSION_SECURE_COOKIE`
- `AUTH_SESSION_SAME_SITE`
- `AUTH_SESSION_COOKIE_DOMAIN`

프론트가 보는 public origin 과 백엔드가 계산하는 cookie 정책이 어긋나면 세션 유지가 쉽게 깨집니다.

## 배포 환경에서 인증이 깨지는 경우

브라우저 기준으로는 `/api` rewrite를 통해 same-origin처럼 동작시키는 구성을 권장합니다.
프론트가 백엔드를 직접 cross-origin 호출하면 쿠키 정책 영향이 더 커집니다.

## 외부 검색 결과가 비거나 일부 provider만 동작하는 경우

외부 provider는 각각 독립적으로 실패할 수 있습니다.
현재 구조는 provider 실패를 기록하고, 가능한 결과만 반환하는 부분 성공 방식입니다.

확인 항목:

- 네트워크 접근 가능 여부
- 캐시 TTL
- 프로그래머스 카탈로그 적재 상태
- 검색어 / 태그 정규화 결과

## 비밀번호 재설정이 실패하는 경우

아래 원인을 먼저 확인합니다.

- 토큰 만료
- 이미 사용한 토큰
- 소셜 전용 계정 여부
- 현재 비밀번호와 동일한 새 비밀번호 사용 여부

## CORS 관련 요청 실패가 나는 경우

credentials 기반 요청을 사용하므로, 허용 origin 목록이 정확해야 합니다.
운영에서는 wildcard pattern보다 정확한 origin 등록이 더 안전합니다.

## Flyway 적용 시 주의사항

기존 운영 DB는 baseline 절차를 먼저 따라야 합니다.
빈 DB와 기존 운영 DB는 적용 방식이 다르므로 [Flyway Baseline 전환 문서](/Users/nayoung/workspace/side-project/ctps/backend/docs/FLYWAY_BASELINE_TRANSITION.md)를 함께 확인해야 합니다.
