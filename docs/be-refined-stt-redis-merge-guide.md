# BE 정제 STT 분리 대응 구현 정리

## 1. 왜 이 작업을 했는가

기존에는 AI 서버의 `after-stt` 응답 한 번에 아래 두 값이 같이 내려왔다.

- 정제된 STT
- 꼬리질문

하지만 실제 처리 자체는 병렬이어도 응답은 하나로 묶여서 오기 때문에, 둘 중 하나가 느리면 다른 하나가 먼저 끝나도 같이 기다려야 했다.

이번 변경의 목적은 이 병목을 줄이는 것이다.

- 꼬리질문 응답은 가능한 빨리 반환한다.
- 정제된 STT는 AI 서버가 정제를 끝내는 즉시 백엔드 내부 API로 따로 전달한다.
- 백엔드는 전달 순서가 바뀌어도 저장이 깨지지 않도록 Redis 임시 저장 후 merge 전략을 사용한다.

---

## 2. 이번에 백엔드에서 바뀐 핵심 아이디어

백엔드는 이제 정제된 STT와 꼬리질문 저장이 서로 다른 시점에 와도 처리할 수 있어야 한다.

처리 규칙은 아래와 같다.

1. 꼬리질문 응답 저장이 먼저 끝난 경우
   정제된 STT callback 이 나중에 오면 `InterviewAnswer` 행이 이미 있으므로 DB의 `answerText`만 즉시 업데이트한다.

2. 정제된 STT callback 이 먼저 도착한 경우
   아직 `InterviewAnswer` 행이 없을 수 있으므로 Redis에 임시 저장한다.

3. 이후 실제 답변 저장 시점이 오면
   Redis에 임시 저장된 정제 텍스트가 있으면 그 값을 우선 사용해서 DB에 저장하고, Redis 값은 제거한다.

즉 최종 규칙은 아래 한 줄로 요약된다.

`Redis 정제 텍스트가 있으면 그것을 우선 저장하고, 없으면 기존 fallback 텍스트를 저장한다.`

---

## 3. 전체 요청 흐름

### 3-1. after-stt 정상 흐름

1. 프론트가 음성을 전송한다.
2. 백엔드는 AI 서버에 `after-stt` 요청을 보낸다.
3. 이때 백엔드는 `interview_id`, `question_id`를 함께 넘긴다.
4. AI 서버는 꼬리질문 생성과 정제 STT 처리를 병렬로 수행한다.
5. 정제 STT가 먼저 끝나면 AI 서버는 백엔드 내부 API로 callback 을 보낸다.
6. 백엔드는 아직 답변 row가 없으면 Redis에 임시 저장한다.
7. 이후 꼬리질문 응답이 도착하면 기존 저장 로직에서 Redis 값을 merge 하여 `InterviewAnswer.answerText`에 저장한다.
8. 응답 DTO의 `sttResult`도 AI 응답 값을 신뢰하지 않고, 최종 저장된 DB 값을 다시 읽어 내려준다.

### 3-2. 꼬리질문 저장이 먼저 끝나는 경우

1. `InterviewAnswer`가 먼저 생성된다.
2. 정제 STT callback 이 나중에 도착한다.
3. 백엔드는 Redis에 넣지 않고 기존 `InterviewAnswer.answerText`만 즉시 갱신한다.

---

## 4. 수정한 파일과 역할

### 4-1. AI 호출부

파일:
- `be/theinterview/src/main/java/com/theinterview/domain/interview/service/AiUtils.java`
- `be/theinterview/src/main/java/com/theinterview/domain/interview/facade/InterviewFacade.java`

역할:
- AI 서버 호출 시 `interview_id`, `question_id`를 같이 넘기도록 변경했다.
- 이유는 AI 서버가 callback 할 때 어느 인터뷰의 어느 질문 답변인지 정확히 식별할 수 있어야 하기 때문이다.

핵심 변경점:
- `callFastApiForNextQuestion(...)`
- `callFastApiForNextQuestionV2(...)`
- `InterviewFacade`에서 위 메서드 호출 시 `interviewId`, `questionId` 전달

추가로 `InterviewFacade`에서는 after-stt 응답을 만들 때 아래 방식으로 바꿨다.

- 이전: AI 응답 안의 정제 텍스트를 그대로 `sttResult`에 사용
- 변경: DB에 실제 저장된 `answerText`를 다시 읽어서 `sttResult`에 사용

이렇게 해야 Redis merge가 발생한 경우에도 응답 텍스트와 DB 값이 일치한다.

---

### 4-2. 저장 merge 로직

파일:
- `be/theinterview/src/main/java/com/theinterview/domain/interview/service/InterviewService.java`

이번 변경의 핵심 로직이 들어간 파일이다.

#### 추가된 상수

- `REFINED_ANSWER_TEMP_TTL`

설명:
- Redis에 임시 저장한 정제 STT의 TTL이다.
- 너무 짧으면 꼬리질문 저장 전에 사라질 수 있고, 너무 길면 불필요한 임시 데이터가 오래 남는다.
- 현재는 10분으로 두었다.

#### 추가된 메서드

- `buildRefinedAnswerRedisKey(Integer interviewId, Integer questionId)`

설명:
- Redis 임시 저장 key를 만드는 메서드다.
- `interviewId + questionId` 조합으로 key를 만들어 서로 다른 인터뷰/질문 간 충돌을 막는다.

- `saveRefinedAnswer(Integer interviewId, Integer questionId, String refinedAnswer)`

설명:
- AI 서버 callback 이 들어왔을 때 실행되는 메서드다.
- 먼저 `questionId`가 실제로 `interviewId`에 속한 질문인지 검증한다.
- 정제 텍스트가 비어 있으면 요청을 거부한다.
- 이미 `InterviewAnswer`가 있으면 DB의 `answerText`만 즉시 갱신한다.
- 아직 `InterviewAnswer`가 없으면 Redis에 임시 저장한다.

- `resolveAnswerText(Integer interviewId, Integer questionId, String fallbackAnswerText)`

설명:
- 실제 DB 저장 직전에 어떤 텍스트를 최종 저장할지 결정하는 공통 메서드다.
- 우선순위는 다음과 같다.

1. Redis에 임시 저장된 정제 STT
2. 현재 저장 로직이 들고 있는 fallback 텍스트

Redis 값을 사용한 경우에는 즉시 삭제해서 같은 데이터가 재사용되지 않게 했다.

또한 기본 pipeline 경로처럼 fallback 텍스트가 비어 있을 수도 있는 흐름을 위해, 아주 짧게 Redis를 재확인하는 retry 도 넣어두었다.

- `getSavedAnswerText(Integer questionId)`

설명:
- after-stt 응답 DTO를 만들 때 실제 최종 저장된 텍스트를 다시 읽기 위한 메서드다.

#### 수정된 기존 저장 메서드

- `saveQuestionAndAnswer(...)`
- `saveAnswer(InterviewQuestion, ...)`
- `saveAnswer(Integer, ...)`
- `saveAiResultAndUnlock(...)`

공통 변경 내용:
- 기존처럼 전달받은 답변 텍스트를 바로 저장하지 않는다.
- 먼저 `resolveAnswerText(...)`를 호출해서 Redis 임시값이 있는지 확인한 뒤 최종 텍스트를 저장한다.

---

### 4-3. 내부 callback API

파일:
- `be/theinterview/src/main/java/com/theinterview/domain/interview/controller/InternalInterviewController.java`
- `be/theinterview/src/main/java/com/theinterview/domain/interview/dto/InterviewRequest.java`

역할:
- AI 서버가 정제된 텍스트를 별도로 보내는 내부 전용 엔드포인트를 추가했다.

엔드포인트:
- `POST /v1/interview/internal/answers/refined`

요청 바디:

```json
{
  "interviewId": 1,
  "questionId": 10,
  "refinedAnswer": "정제된 답변 텍스트"
}
```

DTO:
- `InterviewRequest.SaveRefinedAnswerRequest`

컨트롤러 동작:
- 요청을 받으면 `InterviewService.saveRefinedAnswer(...)`를 호출한다.

---

### 4-4. 엔티티 보조 메서드

파일:
- `be/theinterview/src/main/java/com/theinterview/domain/interview/entity/InterviewAnswer.java`

추가 메서드:
- `updateAnswerText(String answerText)`

역할:
- 답변 row가 이미 존재하는 경우, 오디오 경로나 감정 점수는 건드리지 않고 `answerText`만 안전하게 갱신하기 위한 메서드다.

---

### 4-5. 내부 인증

파일:
- `be/theinterview/src/main/java/com/theinterview/global/security/config/internal/InternalApiTokenFilter.java`
- `be/theinterview/src/main/java/com/theinterview/global/security/config/SecurityConfig.java`
- `be/theinterview/src/main/resources/application-local.yaml`
- `be/theinterview/src/main/resources/application-dev.yaml`

왜 필요한가:
- 기존 구조는 백엔드가 AI 서버를 호출하는 방향만 있었고, 이번 변경은 반대로 AI 서버가 백엔드를 호출한다.
- 이 내부 API를 일반 사용자 JWT 규칙으로 처리하면 구조가 맞지 않으므로, 내부 서버 간 전용 토큰 검증이 필요하다.

동작 방식:
- 내부 경로는 `permitAll`로 열어두었다.
- 대신 `InternalApiTokenFilter`가 해당 경로 요청만 가로채서 `Authorization: Bearer <internal-token>`를 검사한다.
- 토큰이 없거나 틀리면 401을 반환한다.

설정값:
- `internal.api.ai-callback-token`
- 현재 `application-local.yaml`, `application-dev.yaml` 에서 `AI_INTERNAL_TOKEN` 환경변수를 읽도록 연결했다.

---

## 5. 이번 변경에서 중요한 설계 포인트

### 5-1. 왜 Redis를 썼는가

정제 STT callback 이 먼저 와도 DB에 바로 저장할 row가 아직 없을 수 있기 때문이다.

이 상황에서 선택지는 크게 두 가지다.

- DB에 별도 임시 테이블을 둔다.
- Redis에 짧게 저장해 두고 실제 답변 저장 시 merge 한다.

이번에는 구현 영향 범위를 줄이기 위해 Redis 임시 저장 방식을 선택했다.

### 5-2. 왜 응답 DTO에서 DB 값을 다시 읽는가

after-stt 응답 시점에는 다음 두 케이스가 모두 가능하다.

- Redis merge가 이미 발생한 경우
- 아직 callback 이 늦게 와서 fallback 값이 저장된 경우

이때 AI 응답 값을 그대로 내려주면 실제 DB 값과 응답 값이 엇갈릴 수 있다.
그래서 저장 직후 DB의 `answerText`를 다시 읽어 응답에 사용하도록 바꿨다.

### 5-3. 왜 내부 API는 permitAll인데 안전한가

`permitAll`은 Spring Security의 일반 사용자 인증 흐름을 우회하기 위한 설정일 뿐, 실제 무방비 공개가 목적이 아니다.

실제 차단은 `InternalApiTokenFilter`가 먼저 수행한다.
즉 내부 경로는 아래처럼 동작한다.

- 사용자 JWT 검사 전
- 내부 Bearer 토큰 검사
- 통과한 요청만 컨트롤러 진입

---

## 6. 지금 기준으로 확인해야 할 운영 설정

배포 전 확인할 것:

- AI 서버와 백엔드가 같은 내부 토큰 값을 사용하고 있는지
- 백엔드 환경변수 `AI_INTERNAL_TOKEN`이 실제로 주입되는지
- AI 서버가 백엔드 내부 API 경로 `/v1/interview/internal/answers/refined` 로 호출하도록 맞춰져 있는지
- Redis TTL 10분이 실제 인터뷰 처리 시간 기준으로 충분한지

---

## 7. 검증 결과

이번 백엔드 변경 후 아래를 확인했다.

- `./gradlew compileJava` 통과
- 저장 로직은 Redis merge 경로를 타도록 코드 반영 완료
- 내부 callback API와 전용 인증 필터 추가 완료

---

## 8. 나중에 이 코드를 볼 때 먼저 보면 좋은 순서

추천 읽기 순서:

1. `InternalInterviewController`
2. `InterviewService.saveRefinedAnswer(...)`
3. `InterviewService.resolveAnswerText(...)`
4. `saveQuestionAndAnswer(...)`, `saveAnswer(...)`, `saveAiResultAndUnlock(...)`
5. `InterviewFacade`
6. `AiUtils`
7. `SecurityConfig`, `InternalApiTokenFilter`

이 순서로 보면 “callback 수신 -> Redis 임시 저장 -> 실제 저장 시 merge -> 응답 반환” 흐름이 가장 빨리 잡힌다.
