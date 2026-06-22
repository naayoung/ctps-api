# CTPS Backend 이슈 / 트러블슈팅

## 로그인은 되지만 세션 유지가 안 되는 경우

확인 항목:

- `APP_FRONTEND_BASE_URL`
- `APP_BACKEND_BASE_URL`
- `APP_CORS_ALLOWED_ORIGINS`
- `AUTH_SESSION_SECURE_COOKIE`
- `AUTH_SESSION_SAME_SITE`
- `AUTH_SESSION_COOKIE_DOMAIN`

프론트가 보는 public origin과 백엔드가 계산하는 cookie 정책이 어긋나면 세션 유지가 깨질 수 있습니다. Vercel rewrite를 통해 `/api` same-origin 구조를 유지하면 설정이 단순해집니다.

## CSRF 오류가 나는 경우

- `/api/auth/csrf`가 정상 응답하는지 확인합니다.
- `CTPS_CSRF` 쿠키가 브라우저에 저장되는지 확인합니다.
- unsafe method 요청에 `X-CSRF-Token` 헤더가 포함되는지 확인합니다.
- 세션 쿠키와 CSRF 쿠키의 domain/path/sameSite 설정이 같은 배포 origin과 맞는지 확인합니다.

## OAuth 콜백이 실패하는 경우

- provider 콘솔에 등록한 redirect URI가 `/api/auth/oauth/{provider}/callback`과 정확히 일치하는지 확인합니다.
- `APP_FRONTEND_BASE_URL`이 로그인 완료 후 돌아갈 프론트 public URL인지 확인합니다.
- 프론트에서 넘기는 redirect 값은 내부 route만 허용됩니다.

## 외부 검색 결과가 비거나 일부 provider만 동작하는 경우

외부 provider는 각각 독립적으로 실패할 수 있습니다. 현재 구조는 provider 실패를 기록하고 가능한 결과만 반환하는 부분 성공 방식입니다.

확인 항목:

- 네트워크 접근 가능 여부
- 캐시 TTL
- 프로그래머스 카탈로그 적재 상태
- 검색어/태그 정규화 결과
- provider별 warning 응답

## 비밀번호 재설정이 실패하는 경우

아래 원인을 먼저 확인합니다.

- 토큰 만료
- 이미 사용한 토큰
- 소셜 전용 계정 여부
- 현재 비밀번호와 동일한 새 비밀번호 사용 여부
- 운영에서 reset token 노출 설정을 켜지 않았는지 여부

## Flyway 적용 시 주의사항

기존 운영 DB는 baseline 절차를 먼저 따라야 합니다. 빈 DB와 기존 운영 DB는 적용 방식이 다르므로 [Flyway Baseline 전환 문서](./FLYWAY_BASELINE_TRANSITION.md)를 함께 확인합니다.

## 로컬 build가 제한 환경에서 실패하는 경우

샌드박스 환경에서는 Gradle 파일락용 로컬 소켓 생성이 제한될 수 있습니다.

```bash
./gradlew --gradle-user-home .gradle build
```

위처럼 workspace 내부 Gradle home을 사용하면 대부분의 제한 환경에서 build/test를 확인할 수 있습니다.
