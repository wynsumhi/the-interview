# FastAPI 내부 서비스 인증 적용 상세 설명

## 1. 문서 목적

이 문서는 이번 작업에서 실제로 반영된 변경사항을 코드 기준으로 다시 정리한 문서다.

정리 범위는 아래와 같다.

- AI FastAPI에 어떤 인증/인가 로직이 추가되었는지
- 백엔드(Spring)가 왜 수정되어야 했는지
- 실제로 어떤 파일이 어떻게 바뀌었는지
- `FASTAPI_SERVICE_TOKEN`이 어떤 값이며 어디서 관리해야 하는지

이 문서는 MR 설명, 팀 공유, 백엔드 담당자 전달 문서로 바로 활용할 수 있도록 작성했다.

---

## 2. 이번 변경의 본질

이번 변경은 **사용자 인증 추가**가 아니라 **백엔드와 AI 서버 사이의 내부 서비스 인증 추가**다.

즉, 역할은 아래처럼 나뉜다.

- 사용자 인증/인가: Spring Security + JWT를 사용하는 백엔드가 담당
- AI 서버 호출 주체 검증: FastAPI가 내부 서비스 토큰으로 담당

따라서 이번 구조의 핵심은 다음과 같다.

1. 프론트는 기존처럼 백엔드에 요청한다.
2. 백엔드는 사용자 JWT를 검증하고 비즈니스 권한을 확인한다.
3. 백엔드는 AI FastAPI를 호출할 때 내부 서비스 토큰을 Authorization 헤더에 담는다.
4. AI FastAPI는 이 토큰이 등록된 값인지, 그리고 필요한 scope를 가지고 있는지 검사한다.

즉, AI 서버는 "이 요청을 보낸 주체가 신뢰된 백엔드인가?"만 확인하고,  
사용자가 누구인지는 백엔드가 이미 검증했음을 전제로 동작한다.

---

## 3. AI 서버 쪽에서 바뀐 것

### 3.1 추가된 인증 방식

AI FastAPI는 보호 대상 엔드포인트에서 아래 헤더를 요구하도록 바뀌었다.

```http
Authorization: Bearer <internal-service-token>
```

이 토큰은 사용자 JWT가 아니라, 내부 서버 간 호출을 위한 정적 서비스 토큰이다.

### 3.2 토큰과 scope 설정 방식

AI 쪽 설정은 `AUTH_TOKENS` 환경변수로 관리한다.

예시:

```env
AUTH_ENABLED="true"
AUTH_TOKENS="backend-token|ai:invoke,stt:invoke,rag:write"
```

위 설정의 의미는 다음과 같다.

- `backend-token`이라는 토큰을 허용한다.
- 이 토큰은 `ai:invoke`, `stt:invoke`, `rag:write` 권한을 가진다.

즉, 백엔드가 `backend-token`을 헤더에 담아 보내면 AI 서버는 해당 요청을 통과시킨다.

### 3.3 보호 대상 엔드포인트

현재 보호되는 엔드포인트는 아래와 같다.

- `/api/v1/ai/interview/*` : `ai:invoke`
- `/api/v1/ai/stt` : `stt:invoke`
- `/api/v1/ai/interview/rag/*` : `rag:write`

아래는 공개 상태를 유지한다.

- `/`
- `/api/v1/ai/health`

이유는 헬스체크와 서비스 생존 확인은 인증 없이 가능해야 하기 때문이다.

---

## 4. 백엔드가 수정되어야 했던 이유

기존 백엔드는 FastAPI를 그냥 호출하면 됐다.  
하지만 이제 FastAPI 보호 엔드포인트가 내부 서비스 토큰을 요구하므로,
백엔드는 모든 FastAPI 호출에 공통으로 Authorization 헤더를 실어야 한다.

이때 헤더를 각 메서드마다 직접 붙이면 다음 문제가 생긴다.

- 호출 지점마다 중복 코드가 생긴다.
- 새 호출이 추가될 때 헤더 누락 가능성이 있다.
- 토큰 교체 시 수정 범위가 커진다.
- 인증 정책이 여러 파일에 흩어져 유지보수가 어려워진다.

그래서 이번 작업에서는 FastAPI 전용 `WebClient`를 만드는 시점에
기본 Authorization 헤더를 공통 적용하는 방식으로 정리했다.

---

## 5. 실제로 수정된 백엔드 파일

### 5.1 `WebClientConfig.java`

파일:

- `be/theinterview/src/main/java/com/theinterview/global/config/WebClientConfig.java`

수정 내용:

- `fastapi.service-token` 설정값을 읽도록 추가
- FastAPI 전용 `WebClient`에 기본 Authorization 헤더 추가

적용 방식:

```java
builder.defaultHeader("Authorization", "Bearer " + fastApiServiceToken);
```

이렇게 한 이유:

- 모든 FastAPI 호출에 공통 인증 적용
- AiUtils에서 중복 헤더 조립 제거
- 인증 누락 가능성 최소화

### 5.2 `application-local.yaml`, `application-dev.yaml`

파일:

- `be/theinterview/src/main/resources/application-local.yaml`
- `be/theinterview/src/main/resources/application-dev.yaml`

추가된 설정:

```yaml
fastapi:
  base-path: ...
  service-token: ${FASTAPI_SERVICE_TOKEN:}
```

이 의미는:

- 실제 토큰 값을 코드에 박아두지 않는다.
- 실행 환경의 `FASTAPI_SERVICE_TOKEN` 환경변수를 읽어 쓴다.
- 값이 없으면 빈 문자열로 두되, 운영에서는 반드시 넣어야 한다.

### 5.3 `AiUtils.java`

파일:

- `be/theinterview/src/main/java/com/theinterview/domain/interview/service/AiUtils.java`

수정 내용:

- 기존 FastAPI 호출 구조는 유지
- 4xx/5xx 오류 처리 로직을 메서드로 분리
- 특히 `401`, `403`, 일반 `4xx`를 구분해서 로그 남기도록 보강

이유:

기존에는 FastAPI 4xx가 나면 대부분 내부 서버 오류처럼 뭉뚱그려 처리됐다.  
하지만 이제는 4xx 중에서도 아래 차이가 중요해졌다.

- `401`: 토큰 누락 또는 토큰 불일치
- `403`: 토큰은 맞지만 scope 부족
- 일반 `4xx`: 잘못된 요청 body, validation 실패 등

즉, 이번부터는 "왜 실패했는지"를 운영 로그만 봐도 구분할 수 있게 하는 것이 중요해졌다.

---

## 6. 현재 백엔드에서 FastAPI 인증이 실제로 적용되는 흐름

현재 구조를 코드 기준으로 보면 아래와 같다.

1. `application-*.yaml` 에서 `fastapi.service-token`을 읽는다.
2. `WebClientConfig`가 FastAPI 전용 `WebClient` bean을 만든다.
3. 이때 `Authorization: Bearer <FASTAPI_SERVICE_TOKEN>` 헤더를 기본값으로 붙인다.
4. `AiUtils`가 이 `WebClient`를 주입받아 FastAPI를 호출한다.
5. FastAPI는 받은 토큰을 `AUTH_TOKENS` 설정과 비교해 인증/인가를 검사한다.

즉, `AiUtils`는 인증 헤더를 몰라도 된다.  
인증 책임은 `WebClientConfig`에 모아두고, `AiUtils`는 요청 조립과 응답 처리에만 집중하게 만든 구조다.

---

## 7. `FASTAPI_SERVICE_TOKEN`은 어디서 발급받는가

결론부터 말하면, **외부 인증 서버에서 발급받는 값이 아니다.**  
이번 구조에서 `FASTAPI_SERVICE_TOKEN`은 우리가 직접 정해서 양쪽 서버에 동일하게 설정하는 **내부 공유 비밀값(shared secret)** 이다.

즉 개념은 아래와 같다.

- AI 서버:
  `AUTH_TOKENS="backend-token|ai:invoke,stt:invoke,rag:write"`
- 백엔드:
  `FASTAPI_SERVICE_TOKEN=backend-token`

이 둘의 토큰 문자열이 정확히 같아야 한다.

### 7.1 누가 발급하나

사실상 "발급"이라기보다 팀/운영 환경에서 직접 생성하고 주입한다.

보통 방식은 아래 중 하나다.

- 팀에서 임의의 랜덤 문자열을 생성
- 인프라/배포 환경 secret에 저장
- AI 서버와 백엔드에 같은 값으로 주입

예시:

```text
FASTAPI_SERVICE_TOKEN=ti_ai_internal_2026_prod_x8V3sKq...
```

### 7.2 어디에 저장하나

저장 위치는 배포 방식에 따라 다르다.

#### 로컬 개발

- PowerShell 환경변수
- IntelliJ Run Configuration 환경변수
- `.env` 또는 별도 local secret 파일

#### 개발/운영 서버

- Jenkins credential
- GitLab CI/CD variable
- Docker Compose `environment`
- Kubernetes Secret
- EC2/Systemd 환경변수

### 7.3 중요한 점

이 토큰은 사용자 access token처럼 로그인 시마다 바뀌는 값이 아니다.  
운영자가 관리하는 서버 간 고정 비밀값이다.

즉:

- 프론트에 노출되면 안 됨
- Git에 커밋하면 안 됨
- AI 서버와 백엔드 둘 다 같은 값이어야 함

---

## 8. 실제 설정 예시

### 8.1 AI 서버

```env
AUTH_ENABLED="true"
AUTH_TOKENS="backend-token|ai:invoke,stt:invoke,rag:write"
```

### 8.2 백엔드

```env
FASTAPI_SERVICE_TOKEN="backend-token"
```

이렇게 맞추면 백엔드가 FastAPI의 인터뷰, STT, RAG 보호 엔드포인트를 모두 호출할 수 있다.

---

## 9. 백엔드 담당자에게 전달해야 하는 핵심 메시지

백엔드 담당자에게는 아래처럼 설명하면 된다.

1. AI FastAPI 보호 엔드포인트에 내부 서비스 인증이 추가되었다.
2. 백엔드는 FastAPI 호출 시 `Authorization: Bearer <FASTAPI_SERVICE_TOKEN>`을 보내야 한다.
3. 이 토큰은 외부 발급 토큰이 아니라, AI 서버의 `AUTH_TOKENS`에 등록된 문자열과 동일한 값이어야 한다.
4. 헤더 추가는 `WebClientConfig`에 공통 반영되었고, `AiUtils`는 그대로 이 WebClient를 사용한다.
5. 인증 실패 시 `401`, scope 부족 시 `403`이 로그에서 구분되도록 보강되었다.

---

## 10. 지금 시점에서 해야 할 실제 운영 작업

현재 코드 수정 외에 실제로 필요한 작업은 아래다.

### AI 서버 쪽

- 운영/개발 환경에 `AUTH_TOKENS` 설정
- 필요 시 `AUTH_ENABLED=true` 설정

### 백엔드 쪽

- 운영/개발 환경에 `FASTAPI_SERVICE_TOKEN` 설정
- 값은 반드시 AI 서버의 `AUTH_TOKENS`에 등록된 토큰 문자열과 동일해야 함

### 배포/인프라 쪽

- 토큰을 Git이 아닌 secret 저장소로 관리
- 프론트에서 AI 서버를 직접 호출하지 못하도록 구조 유지

---

## 11. 요약

이번 변경은 "AI 서버에 사용자 인증을 붙인 것"이 아니라,  
"백엔드만 AI 서버를 호출할 수 있도록 내부 서비스 인증을 추가한 것"이다.

따라서 `FASTAPI_SERVICE_TOKEN`은 외부에서 발급받는 access token이 아니라,
운영자가 직접 생성해서 AI 서버와 백엔드에 동일하게 주입하는 내부 공유 비밀값이다.

현재 코드상으로는 이미 백엔드가 그 값을 읽어 Authorization 헤더에 자동으로 넣도록 반영되어 있으므로,
남은 작업은 실제 실행 환경에 그 값을 설정하는 것이다.
