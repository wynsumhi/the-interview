# 백엔드 API 명세서 업데이트 가이드

## 1. 문서 목적

이 문서는 백엔드 API 명세 또는 백엔드-AI 연동 명세를 업데이트할 때,
무엇을 추가하고 무엇을 명확히 써야 하는지 정리한 문서다.

핵심은 백엔드 공개 API 자체에 새 인증이 추가된 것이 아니라,
**백엔드가 AI FastAPI를 호출하는 내부 연동 규칙이 바뀌었다**는 점이다.

즉, 백엔드 명세에서는 아래를 분명히 해야 한다.

- 백엔드는 사용자 JWT 인증을 계속 담당한다.
- 백엔드는 AI 서버 호출 시 내부 서비스 토큰을 붙인다.
- 프론트는 AI 서버를 직접 호출하지 않는다.

---

## 2. 백엔드 명세에 추가해야 하는 핵심 설명

## 2.1 인증 책임 분리 섹션 추가

백엔드 문서 또는 시스템 연동 문서에 아래 내용을 추가한다.

```md
## Authentication Responsibility

- End-user authentication and authorization are handled by Backend.
- Backend calls AI FastAPI as an internal trusted service.
- AI FastAPI does not validate end-user JWT directly.
- Backend attaches an internal service token when calling protected AI endpoints.
```

이 섹션은 "왜 AI 서버가 JWT를 직접 검증하지 않는지"를 설명하는 역할도 한다.

---

## 3. 백엔드 명세에서 새로 추가해야 하는 내부 연동 규칙

## 3.1 AI 호출 시 Authorization 헤더 추가

백엔드 내부 연동 명세에는 아래 내용을 추가해야 한다.

```md
When Backend calls protected AI FastAPI endpoints, it must include:

Authorization: Bearer <FASTAPI_SERVICE_TOKEN>
```

설명:

- 이 값은 사용자 access token이 아니다.
- 백엔드와 AI 서버 간 내부 서비스 인증용 토큰이다.
- 프론트에 노출되면 안 된다.

## 3.2 환경변수 요구사항 추가

백엔드 실행환경 명세 또는 설정 문서에 아래 항목을 추가해야 한다.

```md
Required environment variables:

- FASTAPI_SERVICE_TOKEN
```

필요 시 아래도 함께 명시:

```md
The value of FASTAPI_SERVICE_TOKEN must match one of the tokens configured in AI FastAPI AUTH_TOKENS.
```

---

## 4. 백엔드 명세에서 수정해야 하는 기존 전제

기존 문서에 아래 같은 전제가 있으면 수정이 필요하다.

### 수정 대상

- 백엔드는 AI FastAPI를 별도 인증 없이 호출한다
- AI 호출은 단순 HTTP 통신이고 별도 보안 규칙이 없다
- 프론트가 필요한 경우 AI 서버를 직접 호출할 수 있다

### 수정 방향

- 백엔드는 AI 호출 시 내부 서비스 인증을 수행한다
- AI 보호 엔드포인트는 백엔드만 호출한다
- 프론트는 반드시 백엔드를 통해 간접적으로 AI 기능을 사용한다

---

## 5. 백엔드 명세에 추가해야 하는 실행환경 섹션

백엔드 환경설정 문서에 아래 내용을 넣는 것이 좋다.

### 예시

```md
## FastAPI Integration Settings

- fastapi.base-path
- FASTAPI_SERVICE_TOKEN
```

추가 설명:

- `fastapi.base-path`: AI FastAPI 서버 주소
- `FASTAPI_SERVICE_TOKEN`: AI 보호 엔드포인트 호출용 내부 서비스 토큰

---

## 6. 백엔드 명세에서 설명해야 하는 실제 호출 흐름

백엔드 명세서 또는 시스템 시퀀스 설명에 아래 흐름을 추가하면 좋다.

1. 프론트가 사용자 JWT를 포함해 백엔드 API 호출
2. 백엔드가 Spring Security/JWT로 사용자 인증 수행
3. 백엔드가 비즈니스 권한 확인
4. 백엔드가 AI FastAPI 호출 시 내부 서비스 토큰 첨부
5. AI FastAPI가 서비스 토큰과 scope 검사
6. AI 응답을 백엔드가 받아 프론트에 반환

즉, 백엔드 문서에서는 "사용자 인증 후 내부 AI 호출"이라는 흐름을 명확히 써야 한다.

---

## 7. 백엔드 명세서에 반영해야 하는 코드/설정 기준

현재 코드 기준으로 명세에 반영해야 하는 백엔드 변경점은 아래다.

### 7.1 설정 파일

반영 파일:

- `application-local.yaml`
- `application-dev.yaml`

반영 내용:

```yaml
fastapi:
  base-path: ...
  service-token: ${FASTAPI_SERVICE_TOKEN:}
```

문서에 써야 하는 의미:

- 실제 토큰 값은 실행 환경 환경변수에서 주입한다.
- 코드 저장소에는 민감정보를 직접 넣지 않는다.

### 7.2 공통 WebClient 인증

반영 파일:

- `WebClientConfig.java`

문서에 써야 하는 의미:

- FastAPI 전용 WebClient 생성 시 기본 Authorization 헤더를 공통 적용한다.
- 개별 서비스 메서드마다 헤더를 따로 넣지 않는다.

### 7.3 AI 호출 예외 처리 보강

반영 파일:

- `AiUtils.java`

문서에 써야 하는 의미:

- FastAPI 401, 403, 일반 4xx, 5xx를 구분 로그 처리한다.
- 인증 설정 문제와 요청 형식 문제를 운영 로그에서 구분 가능하게 했다.

---

## 8. 백엔드 명세에 추가해야 하는 에러 설명

백엔드 공개 API 응답 스펙이 아니라, 내부 연동 실패 설명에 아래를 추가하는 것이 좋다.

### 추가 설명

- AI 호출 중 `401` 발생 시:
  백엔드의 `FASTAPI_SERVICE_TOKEN` 값 누락 또는 AI 서버 설정과 불일치 가능성

- AI 호출 중 `403` 발생 시:
  토큰은 유효하지만 AI 서버가 요구하는 scope가 부족할 가능성

- AI 호출 중 일반 `4xx` 발생 시:
  payload 형식 오류, validation 실패 가능성

- AI 호출 중 `5xx` 발생 시:
  FastAPI 내부 오류 또는 외부 의존성 장애 가능성

이건 외부 API 문서보다 운영/개발 문서에 특히 중요하다.

---

## 9. 백엔드 명세서에 넣으면 좋은 예시 문구

### 9.1 내부 AI 호출 설명 예시

```md
Backend is responsible for end-user authentication.
After successful user authentication and authorization, Backend calls AI FastAPI as an internal trusted service.
Protected AI endpoints require an internal service token.
```

### 9.2 환경변수 설명 예시

```md
FASTAPI_SERVICE_TOKEN is an internal shared secret for Backend-to-AI communication.
It is not an end-user JWT and must not be exposed to frontend clients.
```

### 9.3 보안 설명 예시

```md
Frontend clients must not call AI FastAPI directly.
All AI-related operations must go through Backend.
```

---

## 10. 백엔드 명세 작성 체크리스트

- [ ] 사용자 인증 책임이 백엔드에 있다는 설명 추가
- [ ] AI 호출 시 내부 서비스 토큰 사용 규칙 추가
- [ ] `FASTAPI_SERVICE_TOKEN` 환경변수 요구사항 추가
- [ ] 프론트 직접 AI 호출 금지 명시
- [ ] AI 연동 실패 시 401/403 의미 설명 추가
- [ ] AI 호출 공통 WebClient 방식 설명 추가

---

## 11. 요약

백엔드 명세에서 중요한 것은 "새 API path"보다
"AI 연동 호출 규칙이 바뀌었다"는 점을 문서화하는 것이다.

즉, 백엔드 문서에는 아래가 들어가야 한다.

- 사용자 인증은 백엔드가 수행
- AI 호출은 내부 서비스 토큰으로 보호
- `FASTAPI_SERVICE_TOKEN` 환경변수 필요
- 프론트는 AI FastAPI를 직접 호출하지 않음
