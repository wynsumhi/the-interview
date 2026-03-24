# AI API 명세서 업데이트 가이드

## 1. 문서 목적

이 문서는 AI FastAPI 명세서를 업데이트할 때,
기존 명세에서 무엇을 추가하고 무엇을 수정해야 하는지 정리한 문서다.

핵심은 이번 변경이 **요청 body 변경**이 아니라
**인증 헤더 요구사항과 오류 응답 체계 추가**라는 점이다.

---

## 2. 이번 변경의 핵심 요약

AI FastAPI는 이제 일부 엔드포인트에 대해 내부 서비스 인증을 요구한다.

요구 헤더:

```http
Authorization: Bearer <internal-service-token>
```

이 토큰은 사용자 JWT가 아니다.  
백엔드와 AI 서버 사이의 내부 호출을 위한 서비스 토큰이다.

따라서 AI API 명세서에는 아래 내용을 반드시 반영해야 한다.

- 보호 대상 엔드포인트 명시
- 공통 Authorization 헤더 요구사항 추가
- `401 Unauthorized`, `403 Forbidden` 응답 추가
- 공개 엔드포인트와 보호 엔드포인트 구분

---

## 3. 명세서에 새로 추가해야 하는 공통 섹션

## 3.1 Authentication 섹션 추가

AI API 명세서 상단 또는 공통 규칙 섹션에 아래 내용을 추가한다.

### 추가할 내용

```md
## Authentication

Protected AI endpoints require an internal service token.

Header:
Authorization: Bearer <internal-service-token>

This token is used for server-to-server communication between Backend and AI FastAPI.
It is not an end-user JWT and must not be exposed to frontend clients.
```

### 같이 적어야 하는 설명

- 이 토큰은 백엔드가 AI 서버를 호출할 때만 사용한다.
- 프론트엔드가 직접 보유하거나 사용하면 안 된다.
- 사용자 인증은 백엔드가 담당하고, AI 서버는 호출 주체만 검증한다.

---

## 4. 공개/보호 엔드포인트 구분표

명세서에는 공개 엔드포인트와 보호 엔드포인트를 분리해서 써야 한다.

### 4.1 공개 엔드포인트

아래 엔드포인트는 인증 없이 접근 가능하다.

| Method | Path | 설명 |
|---|---|---|
| GET | `/` | 루트 확인용 |
| GET | `/api/v1/ai/health` | 헬스체크 |

### 4.2 보호 엔드포인트

아래 엔드포인트는 모두 `Authorization: Bearer <internal-service-token>` 헤더가 필요하다.

| Method | Path | 필요 scope |
|---|---|---|
| POST | `/api/v1/ai/interview/start-tts` | `ai:invoke` |
| POST | `/api/v1/ai/interview/next-opening` | `ai:invoke` |
| POST | `/api/v1/ai/interview/title` | `ai:invoke` |
| POST | `/api/v1/ai/interview/keyword` | `ai:invoke` |
| POST | `/api/v1/ai/interview/pipeline` | `ai:invoke` |
| POST | `/api/v1/ai/interview/pipeline/after-stt` | `ai:invoke` |
| POST | `/api/v1/ai/interview/state/temp-save` | `ai:invoke` |
| POST | `/api/v1/ai/stt` | `stt:invoke` |
| POST | `/api/v1/ai/interview/rag/upsert` | `rag:write` |
| DELETE | `/api/v1/ai/interview/rag/delete` | `rag:write` |
| POST | `/api/v1/ai/interview/rag/reindex` | `rag:write` |

---

## 5. 각 엔드포인트 명세에 추가해야 하는 항목

기존 body schema, form-data 필드, 응답 body는 대부분 그대로 유지된다.  
따라서 각 엔드포인트 문서에는 아래 항목을 추가하는 식으로 업데이트하면 된다.

### 5.1 Header Requirements

각 보호 엔드포인트 명세에 아래 섹션 추가:

```md
### Headers

- Authorization: Bearer <internal-service-token>
```

### 5.2 Security Description

각 보호 엔드포인트 설명에 아래와 유사한 문구 추가:

```md
This endpoint is intended to be called by the backend service only.
The backend must complete end-user authentication and authorization before forwarding the request to the AI server.
```

---

## 6. 에러 응답 명세에 추가해야 하는 항목

기존에는 주로 `400`, `422`, `500` 위주였다면, 이제 아래 응답도 포함해야 한다.

### 6.1 401 Unauthorized

설명:

- Authorization 헤더 누락
- Bearer 토큰 값 불일치

예시:

```json
{
  "detail": "Missing bearer token."
}
```

또는

```json
{
  "detail": "Invalid bearer token."
}
```

### 6.2 403 Forbidden

설명:

- 토큰은 맞지만 필요한 scope가 없음

예시:

```json
{
  "detail": "Missing required scope: ai:invoke"
}
```

엔드포인트에 따라 scope 값은 달라진다.

- `ai:invoke`
- `stt:invoke`
- `rag:write`

---

## 7. 기존 명세에서 수정해야 하는 문구

기존 명세 중 아래 뉘앙스가 있으면 수정해야 한다.

### 수정 대상

- AI 서버는 별도 인증 없이 호출 가능하다
- 클라이언트가 AI 서버를 직접 호출할 수 있다
- 추가 헤더 없이 요청 body만 맞으면 된다

### 수정 방향

- AI 서버는 내부 서비스 전용 API라고 명시
- 보호 대상 엔드포인트는 반드시 Authorization 헤더 필요
- 프론트는 직접 호출하지 않고 백엔드를 통해서만 접근

---

## 8. 기존 명세에서 사실상 제거해야 하는 전제

문구로 안 적혀 있더라도 아래 가정은 제거된 것으로 봐야 한다.

- 프론트엔드가 AI FastAPI를 직접 호출해도 된다
- 사용자 JWT가 AI 서버까지 그대로 전달된다
- AI 서버가 사용자 인증을 직접 처리한다

현재 구조는 아래가 맞다.

- 사용자 인증: 백엔드
- 내부 서비스 인증: AI 서버

---

## 9. 엔드포인트별 반영 예시

예를 들어 `POST /api/v1/ai/interview/start-tts` 명세에 아래를 추가하면 된다.

```md
### Headers

- Authorization: Bearer <internal-service-token>

### Security

This endpoint is protected for backend-to-AI communication only.
The backend must authenticate the user before calling this API.

### Additional Error Responses

401 Unauthorized
- Missing bearer token
- Invalid bearer token

403 Forbidden
- Missing required scope: ai:invoke
```

같은 방식으로 `stt`, `rag` 문서에도 적용하면 된다.

---

## 10. AI 명세서 작성 체크리스트

- [ ] 공통 Authentication 섹션 추가
- [ ] 공개 엔드포인트 / 보호 엔드포인트 구분표 추가
- [ ] 보호 엔드포인트마다 Authorization 헤더 요구사항 추가
- [ ] 401 응답 추가
- [ ] 403 응답 추가
- [ ] "프론트 직접 호출 가능" 전제 제거
- [ ] "사용자 JWT를 AI가 직접 검증한다"는 오해 소지 문구 제거

---

## 11. 요약

AI API 명세서에서 이번에 중요한 것은 body schema 변경이 아니라
**인증 헤더 요구사항과 보호 범위 명시**다.

즉, 기존 명세를 전부 갈아엎을 필요는 없고,
기존 엔드포인트 정의 위에 아래를 추가하는 식으로 업데이트하면 된다.

- Authorization 헤더
- 401/403 응답
- 내부 서비스 전용 API라는 설명
