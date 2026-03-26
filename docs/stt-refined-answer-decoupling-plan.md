# STT 정제 응답 분리 작업 정리

## 목표

현재 `after-stt` 파이프라인 응답에는 아래 두 결과가 같이 포함된다.

- 정제된 STT (`refined_answer`)
- 꼬리질문 + TTS + 상태 분석 결과

내부 처리 자체는 병렬이지만 최종 응답이 하나로 묶여 있기 때문에, `refined_answer`가 먼저 끝나도 `follow-up question`이 끝날 때까지 기다려야 한다.  
이번 변경의 목표는 `정제된 STT`를 `after-stt` 응답에서 분리하고, 정제 완료 즉시 AI 서버가 백엔드 저장 API를 호출해서 별도로 저장하도록 바꾸는 것이다.

즉, 최종 방향은 다음과 같다.

1. 백엔드가 기존처럼 AI 서버의 `after-stt`를 호출한다.
2. AI 서버는 내부적으로 `정제 STT`와 `꼬리질문 생성`을 병렬 수행한다.
3. `정제 STT`가 먼저 끝나면, AI 서버가 백엔드의 신규 내부 API를 즉시 호출해 DB 저장을 요청한다.
4. `after-stt` 응답에는 더 이상 `refined_answer`를 포함하지 않는다.
5. 백엔드는 `after-stt` 응답으로는 꼬리질문 관련 결과만 받고, 정제 텍스트 저장은 별도 내부 API 흐름으로 처리한다.

## 핵심 설계 원칙

- 사용자용 공개 API가 아니라 AI 서버 전용 내부 API로 만든다.
- 백엔드 보안은 `permitAll()` 완전 오픈이 아니라 내부 토큰 검증 방식으로 처리한다.
- DB 저장 단위는 기존 `InterviewAnswer.answerText`를 사용하는 방향을 우선 가정한다.
- 동일 답변에 대해 중복 저장 요청이 들어와도 안전하도록 idempotency를 고려한다.
- 실패 시 꼬리질문 응답을 막지 않도록 저장 API 호출 실패는 별도 로깅/재시도 정책으로 분리한다.

## 현재 구조 요약

### AI 서버

- `after-stt` 엔드포인트: [ai/app/api/v1/endpoints/interview.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/api/v1/endpoints/interview.py)
- 응답 스키마: [ai/app/api/v1/schemas/interview.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/api/v1/schemas/interview.py)
- 응답 조립 서비스: [ai/app/services/interview_service.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/services/interview_service.py)
- 병렬 처리 로직: [ai/app/pipeline/interview_pipeline.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/pipeline/interview_pipeline.py)

현재 `run_interview_pipeline_from_stt_text()`에서:

- `question_future`: 꼬리질문 생성
- `magazine_future`: 정제 답변 생성

를 병렬 실행하지만, 두 future를 모두 `result()`로 기다린 뒤 하나의 응답 DTO로 합친다.

### 백엔드

- AI 호출 유틸: [be/theinterview/src/main/java/com/theinterview/domain/interview/service/AiUtils.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/service/AiUtils.java)
- 인터뷰 파사드: [be/theinterview/src/main/java/com/theinterview/domain/interview/facade/InterviewFacade.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/facade/InterviewFacade.java)
- 인터뷰 서비스: [be/theinterview/src/main/java/com/theinterview/domain/interview/service/InterviewService.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/service/InterviewService.java)
- 보안 설정: [be/theinterview/src/main/java/com/theinterview/global/security/config/SecurityConfig.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/global/security/config/SecurityConfig.java)
- FastAPI 응답 DTO: [be/theinterview/src/main/java/com/theinterview/domain/interview/dto/FastApiResponse.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/dto/FastApiResponse.java)
- 답변 엔티티: [be/theinterview/src/main/java/com/theinterview/domain/interview/entity/InterviewAnswer.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/entity/InterviewAnswer.java)

현재는 `AiPipelineResponse.refinedAnswer`를 받은 뒤, `InterviewService.saveAiResultAndUnlock()` 또는 `saveQuestionAndAnswer()`에서 `InterviewAnswer.answerText`로 저장한다.

## 권장 변경 방식

### 전체 흐름

1. 백엔드는 기존처럼 `after-stt`를 호출한다.
2. AI 서버는 `refined_answer`를 응답에서 제거한다.
3. AI 서버는 `refined_answer` 생성이 끝나는 즉시 백엔드 내부 API를 호출한다.
4. 백엔드는 해당 내부 API에서 `questionId` 기준으로 정제 답변을 저장 또는 업데이트한다.
5. 이후 꼬리질문 응답은 기존 파이프라인대로 백엔드가 처리한다.

### 저장 기준 키

가장 안전한 저장 기준은 `questionId`다.

- 이미 현재 흐름에서 한 질문에 한 답변 구조다.
- 정제된 STT는 “현재 질문에 대한 답변”이므로 `questionId`와 자연스럽게 매핑된다.
- `interviewId`만으로는 어느 질문 답변인지 모호하다.

권장 요청 payload:

- `interviewId`
- `questionId`
- `refinedAnswer`

선택적으로 넣을 수 있는 값:

- `requestId` 또는 `traceId`
- `savedAt`

## AI 서버 작업

### 1. `after-stt` 응답에서 `refined_answer` 제거

수정 파일:

- [ai/app/api/v1/schemas/interview.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/api/v1/schemas/interview.py)
- [ai/app/services/interview_service.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/services/interview_service.py)
- [ai/app/pipeline/interview_pipeline.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/pipeline/interview_pipeline.py)

해야 할 일:

- `InterviewPipelineResponse`에서 `refined_answer` 필드를 제거한다.
- `InterviewPipelineResult` dataclass에서 `refined_answer`를 유지할지 분리할지 결정한다.
- 추천은 내부 계산값으로는 유지하되, `to_response_dict()`에서는 제외하는 방식이다.
- `run_interview_pipeline_from_upload()`, `run_interview_pipeline_from_stt_payload()`에서 응답 dict에 `refined_answer`를 더 이상 넣지 않는다.

권장 이유:

- 내부 병렬 처리 결과값은 여전히 필요할 수 있다.
- 응답 제거와 내부 처리 제거를 한 번에 하지 않으면 리스크를 줄일 수 있다.

### 2. AI 서버에서 백엔드 내부 저장 API 호출 클라이언트 추가

수정 파일 후보:

- 신규 파일 권장: `ai/app/services/backend_callback_service.py`
- 또는 기존 서비스 확장: [ai/app/services/interview_service.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/services/interview_service.py)
- 설정 파일: [ai/app/core/settings.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/core/settings.py)

해야 할 일:

- 백엔드 내부 저장 API URL 설정값 추가
- 내부 호출용 토큰 설정값 추가
- HTTP 클라이언트 함수 추가

권장 설정 예시:

- `BACKEND_INTERNAL_BASE_URL`
- `BACKEND_INTERNAL_TOKEN`
- `BACKEND_INTERNAL_TIMEOUT_SECONDS`

권장 함수 예시:

- `save_refined_answer_to_backend(interview_id: int, question_id: int, refined_answer: str) -> None`

이 함수에서 할 일:

- 백엔드 내부 API로 `POST` 또는 `PUT` 요청
- `Authorization: Bearer <BACKEND_INTERNAL_TOKEN>` 헤더 부착
- 실패 시 예외를 바로 파이프라인 전체 실패로 올릴지, 로그만 남길지 정책 결정

권장 정책:

- 기본은 “로그 + 메트릭 + 선택적 재시도”
- `after-stt` 사용자 응답 자체는 막지 않는 방향

### 3. 병렬 처리 완료 시점 구조 조정

수정 파일:

- [ai/app/pipeline/interview_pipeline.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/pipeline/interview_pipeline.py)
- [ai/app/services/interview_service.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/services/interview_service.py)
- [ai/app/api/v1/endpoints/interview.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/api/v1/endpoints/interview.py)

현재 문제:

- `question_future.result()`와 `magazine_future.result()`를 모두 기다린다.

변경 방향:

- `magazine_future`가 완료되면 즉시 백엔드 내부 저장 API를 호출할 수 있어야 한다.
- `question_future`는 기존대로 꼬리질문 생성 완료까지 기다린다.
- 즉, 내부적으로는 다음과 같은 흐름이 필요하다.

권장 구조:

1. `question_future`, `magazine_future` 시작
2. `magazine_future` 완료 즉시 `save_refined_answer_to_backend(...)` 호출
3. `question_future` 완료 후 `tts_audio` 생성
4. 최종 응답 반환

구현 주의점:

- 지금 `run_interview_pipeline_from_stt_text()` 시그니처에는 `interviewId`, `questionId`가 없다.
- 현재 `after-stt` 요청에는 `sequence`, `nickname`, `previous_question` 등만 넘어오고 있다.
- 따라서 백엔드 저장 API를 호출하려면 AI 서버가 `questionId`를 알아야 한다.

필수 추가 입력:

- `after-stt` 요청에 `interview_id`
- `after-stt` 요청에 `question_id`

수정 파일:

- [ai/app/api/v1/endpoints/interview.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/api/v1/endpoints/interview.py)
- [ai/app/services/interview_service.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/services/interview_service.py)
- [be/theinterview/src/main/java/com/theinterview/domain/interview/service/AiUtils.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/service/AiUtils.java)
- [be/theinterview/src/main/java/com/theinterview/domain/interview/facade/InterviewFacade.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/facade/InterviewFacade.java)

### 4. 예외 처리 정책 정의

수정 파일 후보:

- 신규: `ai/app/services/backend_callback_service.py`
- 기존: [ai/app/services/interview_service.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/services/interview_service.py)

정해야 할 것:

- 저장 API 호출 실패 시 `after-stt`를 실패로 볼지
- 재시도를 둘지
- 로그 필드를 무엇으로 남길지

권장 정책:

- 1차 구현은 `warning/error log` 남기고 `after-stt`는 계속 성공 처리
- 이후 필요하면 retry 또는 dead-letter 성격의 재처리 도입

최소 로그 필드:

- `interview_id`
- `question_id`
- `request_path`
- `error_message`

### 5. AI 서버 인증 설정 추가

수정 파일:

- [ai/app/core/settings.py](/Users/SSAFY/Desktop/S14P21A307/ai/app/core/settings.py)
- `.env` 계열 파일 예시 업데이트: [ai/.env.example](/Users/SSAFY/Desktop/S14P21A307/ai/.env.example)

해야 할 일:

- 백엔드 내부 API 호출용 토큰/URL 환경변수 추가
- 배포 환경에서도 동일 값 주입 가능하게 문서화

### 6. AI 서버 테스트 수정

수정 파일 후보:

- [ai/tests/api/test_interview.py](/Users/SSAFY/Desktop/S14P21A307/ai/tests/api/test_interview.py)
- [ai/tests/pipeline/test_interview_pipeline.py](/Users/SSAFY/Desktop/S14P21A307/ai/tests/pipeline/test_interview_pipeline.py)
- [ai/tests/services/test_interview_service.py](/Users/SSAFY/Desktop/S14P21A307/ai/tests/services/test_interview_service.py)

해야 할 일:

- `after-stt` 응답 assertion에서 `refined_answer` 제거
- 저장 API 호출 함수가 호출되는지 mock 검증 추가
- 저장 API 실패 시에도 `question_text`, `tts_audio_base64`가 정상 응답되는지 테스트 추가

## 백엔드 작업

### 1. AI 서버 전용 내부 저장 API 신규 추가

수정 파일 후보:

- 신규 controller 권장:
  - `be/theinterview/src/main/java/com/theinterview/domain/interview/controller/InternalInterviewController.java`
- 또는 기존 controller 확장:
  - [be/theinterview/src/main/java/com/theinterview/domain/interview/controller/InterviewController.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/controller/InterviewController.java)

권장:

- 일반 사용자 API와 분리하기 위해 `InternalInterviewController` 신규 생성

권장 엔드포인트 예시:

- `POST /api/v1/interview/internal/answers/refined`

권장 request body:

- `interviewId`
- `questionId`
- `refinedAnswer`

권장 response:

- `status`
- `message`

### 2. 내부 요청 DTO 추가

수정 파일:

- [be/theinterview/src/main/java/com/theinterview/domain/interview/dto/InterviewRequest.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/dto/InterviewRequest.java)

해야 할 일:

- 내부 저장 요청용 DTO 추가

예시:

- `SaveRefinedAnswerRequest`
  - `Integer interviewId`
  - `Integer questionId`
  - `String refinedAnswer`

필드 검증 포인트:

- `questionId` null 금지
- `refinedAnswer` blank 금지

### 3. 서비스 로직 추가

수정 파일:

- [be/theinterview/src/main/java/com/theinterview/domain/interview/service/InterviewService.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/service/InterviewService.java)
- 필요 시 repository 활용:
  - [be/theinterview/src/main/java/com/theinterview/domain/interview/Repository/InterviewAnswerRepository.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/Repository/InterviewAnswerRepository.java)

핵심 문제:

- 현재 `InterviewAnswer`는 대부분 생성 시점에 `answerText`를 넣는 구조다.
- 정제 STT를 먼저 저장하려면 “답변이 아직 없을 수도 있는 시점”을 고려해야 한다.

선택지 A. `InterviewAnswer`를 먼저 생성하고 이후 업데이트

장점:

- 정제 답변이 먼저 도착해도 저장 가능

단점:

- 현재 흐름과 차이가 커진다.
- `answer_audio_url`, 감정 점수 등 not null 컬럼 때문에 엔티티/스키마 수정이 커질 수 있다.

선택지 B. 기존 저장 시점을 유지하고, 저장 API는 기존 answer row가 생긴 뒤 update만 수행

장점:

- 엔티티 변경이 적다.

단점:

- “정제되는 즉시 저장” 요구를 충족하지 못할 수 있다.

선택지 C. Redis 임시 저장 후 최종 DB 반영

장점:

- DB 스키마 변경을 최소화하면서 즉시 수신 가능

단점:

- 사용자가 원한 “DB 즉시 저장”과는 다를 수 있다.

이번 요구 기준 권장안:

- 정말 “DB 즉시 저장”이 필요하면 `InterviewAnswer` 저장 모델을 손봐야 한다.
- 다만 현재 구조를 최대한 보존하려면 “내부 API 수신 -> 임시 저장 -> 최종 merge”가 더 현실적일 수 있다.

### 4. DB 즉시 저장을 진짜 하려면 엔티티/스키마 재검토

수정 파일 후보:

- [be/theinterview/src/main/java/com/theinterview/domain/interview/entity/InterviewAnswer.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/entity/InterviewAnswer.java)
- [be/theinterview/src/main/resources/schema.sql](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/resources/schema.sql)

현재 제약:

- `answer_text` not null
- `answer_audio_url` not null
- 감정 점수 필드 not null

즉, “정제 텍스트만 먼저 저장”하려면 다음 중 하나가 필요하다.

1. `InterviewAnswer`를 부분 저장 가능하게 컬럼 nullable 변경
2. `InterviewAnswer`와 별도의 `RefinedAnswerTemp` 테이블 신설
3. Redis 등 임시 저장소 사용

가장 구조적으로 깔끔한 방법:

- 신규 테이블 도입

예시 테이블:

- `interview_answer_refined_temp`
  - `question_id`
  - `interview_id`
  - `refined_answer`
  - `created_at`
  - `updated_at`
  - unique(`question_id`)

그 후 최종 `saveQuestionAndAnswer()` 또는 `saveAiResultAndUnlock()` 시점에 merge

하지만 사용자가 “DB 저장 API”만 원한 것이고, 반드시 신규 테이블까지 원하는 것은 아닐 수 있으므로 아래 2안 중 하나를 먼저 결정해야 한다.

- `InterviewAnswer` 직접 업데이트 중심으로 갈지
- 임시 테이블을 둘지

### 5. 현재 구조 기준의 현실적인 권장안

권장안:

- 백엔드 내부 저장 API는 먼저 만든다.
- 내부 저장 API는 우선 “임시 저장소” 역할을 하게 둔다.
- 최종 답변 저장 시점에 기존 `InterviewAnswer.answerText`와 merge한다.

이 방식의 장점:

- 레이턴시 문제를 먼저 해결할 수 있다.
- DB 모델을 한 번에 크게 뒤집지 않아도 된다.
- 이후 필요 시 임시 저장소를 정식 모델로 확장할 수 있다.

### 6. 내부 인증/인가 추가

수정 파일:

- [be/theinterview/src/main/java/com/theinterview/global/security/config/SecurityConfig.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/global/security/config/SecurityConfig.java)
- 신규 필터/인터셉터 권장:
  - `be/theinterview/src/main/java/com/theinterview/global/security/config/internal/InternalApiTokenFilter.java`
- 설정 파일:
  - [be/theinterview/src/main/resources/application-local.yaml](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/resources/application-local.yaml)
  - [be/theinterview/src/main/resources/application-dev.yaml](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/resources/application-dev.yaml)

해야 할 일:

- AI 서버가 호출할 내부 API 전용 토큰 환경변수 추가
- 내부 경로에 대해서는 JWT 인증 대신 내부 토큰 검증 적용

권장 방식:

1. `/v1/interview/internal/**` 경로 신설
2. 해당 경로는 전용 필터에서 `Authorization: Bearer <AI_INTERNAL_TOKEN>` 검증
3. 일반 사용자 JWT와 분리

주의:

- 단순히 `permitAll()`만 추가하고 토큰 검증을 안 하면 안 된다.

### 7. AI 호출 request에 `interviewId`, `questionId` 추가

수정 파일:

- [be/theinterview/src/main/java/com/theinterview/domain/interview/service/AiUtils.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/service/AiUtils.java)
- [be/theinterview/src/main/java/com/theinterview/domain/interview/facade/InterviewFacade.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/facade/InterviewFacade.java)

해야 할 일:

- `callFastApiForNextQuestionV2()` multipart body에 `interview_id`, `question_id` 포함
- `generateNextQuestionFacade()`에서 현재 값 전달

이유:

- AI 서버가 백엔드에 저장 callback을 보낼 때 식별자가 필요하다.

### 8. FastAPI 응답 DTO 수정

수정 파일:

- [be/theinterview/src/main/java/com/theinterview/domain/interview/dto/FastApiResponse.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/dto/FastApiResponse.java)
- 필요한 경우:
  - [be/theinterview/src/main/java/com/theinterview/domain/interview/dto/InterviewResponse.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/dto/InterviewResponse.java)

해야 할 일:

- `AiPipelineResponse.refinedAnswer` 제거
- 그 값을 쓰던 코드 정리

영향 포인트:

- `saveAiResultAndUnlock()`
- `new FollowUpQuestionDTO(aiPipelineResponse, ...)`
- `InterviewResponse` 내부 생성자/매핑 로직

### 9. 저장 로직 분리

수정 파일:

- [be/theinterview/src/main/java/com/theinterview/domain/interview/service/InterviewService.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/service/InterviewService.java)
- [be/theinterview/src/main/java/com/theinterview/domain/interview/facade/InterviewFacade.java](/Users/SSAFY/Desktop/S14P21A307/be/theinterview/src/main/java/com/theinterview/domain/interview/facade/InterviewFacade.java)

해야 할 일:

- 기존 `AiPipelineResponse.getRefinedAnswer()` 의존 제거
- 정제된 답변은 내부 저장 API 흐름 쪽에서 저장하도록 이동
- 꼬리질문 API 응답 처리 로직은 `question_text`, `tts_audio_base64`, `interview_state_db` 중심으로 유지

### 10. 백엔드 테스트 추가/수정

수정 파일 후보:

- 인터뷰 서비스/컨트롤러 테스트 전반
- 현재 테스트 파일 구조에 맞춰 신규 테스트 추가 필요

필수 테스트 시나리오:

- 내부 저장 API가 유효한 내부 토큰 없으면 401 또는 403
- 내부 저장 API가 정제 텍스트를 정상 저장
- `after-stt` 응답 DTO에서 `refined_answer`가 사라져도 기존 follow-up 저장 흐름이 동작
- 저장 callback이 먼저 오고 follow-up 응답이 나중에 와도 정합성이 유지

## 사전 결정이 필요한 항목

### 1. 진짜 DB 즉시 저장인지, 임시 저장인지

반드시 먼저 결정해야 한다.

- `InterviewAnswer` 직접 부분 저장
- 임시 테이블 저장 후 merge
- Redis 임시 저장 후 merge

현재 코드 구조상 가장 자연스러운 것은 임시 저장 테이블 추가다.

### 2. AI 서버 callback 실패 시 정책

선택지:

- 실패해도 꼬리질문 응답은 성공
- 실패하면 `after-stt` 전체 실패

권장:

- 실패해도 꼬리질문 응답은 성공
- 실패 건은 로그와 모니터링으로 추적

### 3. 내부 토큰 방식

권장:

- 별도 `AI_TO_BE_INTERNAL_TOKEN`
- Bearer 헤더 사용

비권장:

- 사용자 JWT 재사용
- IP 화이트리스트만으로 신뢰
- `permitAll()` 오픈

## 권장 구현 순서

### 1단계

- 백엔드 내부 저장 API 설계 확정
- 저장 위치를 `InterviewAnswer 직접`, `임시 테이블`, `Redis 임시 저장` 중 결정

### 2단계

- 백엔드 내부 인증 필터와 내부 API 구현
- 단위 테스트 작성

### 3단계

- 백엔드에서 FastAPI `after-stt` 요청에 `interview_id`, `question_id` 전달

### 4단계

- AI 서버에서 callback 클라이언트 추가
- `refined_answer` 생성 완료 시 백엔드 내부 API 호출 연결

### 5단계

- AI 서버 `after-stt` 응답에서 `refined_answer` 제거
- AI/BE 테스트 전체 수정

### 6단계

- 실패 로그, 재시도 정책, 운영 환경 env 정리

## 최소 변경 버전과 권장 버전

### 최소 변경 버전

- `after-stt` 응답에서 `refined_answer` 제거
- AI 서버가 정제 완료 시 백엔드 내부 API 호출
- 백엔드는 임시 저장소에 저장
- 최종 저장 시 기존 흐름과 merge

장점:

- 현재 구조를 덜 건드린다.

단점:

- 데이터 저장 지점이 이원화된다.

### 권장 버전

- 내부 API + 내부 인증 추가
- 임시 저장 테이블 도입
- `after-stt` 응답 완전 분리
- 최종 merge 로직 명시화

장점:

- 책임 분리가 명확하다.
- 운영 중 추적과 복구가 쉽다.

단점:

- 초기 작업량이 더 크다.

## 최종 체크리스트

- AI 서버 `after-stt` request에 `interview_id`, `question_id`가 포함되는가
- AI 서버 `after-stt` response에서 `refined_answer`가 제거되었는가
- AI 서버가 정제 완료 즉시 백엔드 내부 API를 호출하는가
- 백엔드 내부 API가 내부 토큰 없이는 차단되는가
- 정제 텍스트 저장 실패가 꼬리질문 응답 레이턴시를 막지 않는가
- 기존 인터뷰 종료, 제목 생성, 다음 오프닝 생성 흐름에 영향이 없는가

