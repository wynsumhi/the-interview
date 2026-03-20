# RAG API Spec

## 개요

사용자의 매거진 인터뷰 이력을 RAG로 적재, 삭제, 재구성하기 위한 API 명세입니다.

- Base URL: `/api/v1/ai/interview/rag`
- 임베딩 모델: `Qwen/Qwen3-Embedding-0.6B`
- 임베딩 호출 방식: OpenAI 호환 `/v1/embeddings`
- 벡터 저장소: Qdrant
- 컬렉션 이름: `interview_history`
- 벡터 차원: `1024`

## 공통 규칙

- 인터뷰 식별은 `interview_id`를 기준으로 합니다.
- 문답 식별은 `question_id`, `answer_id`를 기준으로 합니다.
- 기존 문서의 `history_id`, `session_id` 개념은 사용하지 않습니다.
- `metadata`는 인터뷰 단위와 문답 단위 모두 선택 필드입니다.
- 현재 기준으로 `metadata`에는 인터뷰 제목 정도만 넣는 것을 권장합니다.

## 1. Upsert Interview RAG

### Endpoint

```http
POST /api/v1/ai/interview/rag/upsert
```

### 목적

새로운 매거진 인터뷰 문답이 생성되었을 때 해당 인터뷰만 사용자 RAG에 추가 적재합니다.

### Request Body

```json
{
  "user_id": "user-123",
  "interview_id": "magazine-interview-001",
  "turns": [
    {
      "question_id": "question-001",
      "answer_id": "answer-001",
      "question": "최근 작업 중 가장 애착이 가는 프로젝트는 무엇인가요?",
      "answer": "오래 준비한 독립 잡지 리디자인 프로젝트가 가장 기억에 남아요.",
      "turn_index": 1,
      "metadata": {
        "title": "디자이너 김하늘 인터뷰"
      }
    }
  ],
  "metadata": {
    "title": "디자이너 김하늘 인터뷰"
  }
}
```

### Request Fields

- `user_id`: 사용자 식별자
- `interview_id`: 매거진 인터뷰 식별자
- `turns`: 인터뷰 문답 목록
- `turns[].question_id`: 질문 식별자
- `turns[].answer_id`: 답변 식별자
- `turns[].question`: 질문 원문
- `turns[].answer`: 답변 원문
- `turns[].turn_index`: 인터뷰 내 문답 순서, 1 이상
- `turns[].metadata`: 문답 단위 메타데이터, 선택
- `metadata`: 인터뷰 단위 메타데이터, 선택

### Success Response

```json
{
  "status": "completed",
  "message": "Interview history RAG upsert completed.",
  "collection_name": "interview_history",
  "indexed_count": 1
}
```

### Validation Rules

- `user_id`, `interview_id`는 필수입니다.
- `turns`는 최소 1개 이상이어야 합니다.
- 각 turn의 `question_id`, `answer_id`, `question`, `answer`는 필수입니다.
- `turn_index`는 1 이상이어야 합니다.

## 2. Delete Interview RAG

### Endpoint

```http
DELETE /api/v1/ai/interview/rag/delete
```

### 목적

특정 매거진 인터뷰를 사용자 RAG에서 제거합니다.

### Request Body

```json
{
  "user_id": "user-123",
  "interview_id": "magazine-interview-001"
}
```

### Request Fields

- `user_id`: 사용자 식별자
- `interview_id`: 삭제할 매거진 인터뷰 식별자

### Success Response

```json
{
  "status": "completed",
  "message": "Interview history RAG delete completed.",
  "collection_name": "interview_history",
  "deleted_count": 1
}
```

### Validation Rules

- `user_id`, `interview_id`는 필수입니다.

## 3. Reindex User Interview RAG

### Endpoint

```http
POST /api/v1/ai/interview/rag/reindex
```

### 목적

사용자의 전체 매거진 인터뷰 이력을 기준으로 RAG를 처음부터 다시 구성합니다.

### 사용 시점

- 임베딩 정책이 바뀐 경우
- 기존 적재 데이터를 전체 재구성해야 하는 경우
- 사용자 인터뷰 전체를 한 번에 동기화해야 하는 경우

### Request Body

```json
{
  "user_id": "user-123",
  "interviews": [
    {
      "interview_id": "magazine-interview-001",
      "turns": [
        {
          "question_id": "question-001",
          "answer_id": "answer-001",
          "question": "최근 작업 중 가장 애착이 가는 프로젝트는 무엇인가요?",
          "answer": "오래 준비한 독립 잡지 리디자인 프로젝트가 가장 기억에 남아요.",
          "turn_index": 1,
          "metadata": {
            "title": "디자이너 김하늘 인터뷰"
          }
        }
      ],
      "metadata": {
        "title": "디자이너 김하늘 인터뷰"
      }
    },
    {
      "interview_id": "magazine-interview-002",
      "turns": [
        {
          "question_id": "question-010",
          "answer_id": "answer-010",
          "question": "요즘 가장 자주 기록하는 소재는 무엇인가요?",
          "answer": "도시에서 마주치는 사소한 장면들을 짧게 메모하고 있어요.",
          "turn_index": 1,
          "metadata": {
            "title": "에세이스트 박서윤 인터뷰"
          }
        }
      ],
      "metadata": {
        "title": "에세이스트 박서윤 인터뷰"
      }
    }
  ]
}
```

### Request Fields

- `user_id`: 사용자 식별자
- `interviews`: 사용자의 전체 매거진 인터뷰 목록
- `interviews[].interview_id`: 인터뷰 식별자
- `interviews[].turns`: 인터뷰 문답 목록
- `interviews[].metadata`: 인터뷰 단위 메타데이터, 선택
- `interviews[].turns[]`: `upsert`의 turn 구조와 동일

### Success Response

```json
{
  "status": "completed",
  "message": "Interview history RAG reindex completed.",
  "collection_name": "interview_history",
  "reindexed_interview_count": 2,
  "reindexed_turn_count": 2
}
```

### Validation Rules

- `user_id`는 필수입니다.
- `interviews`는 최소 1개 이상이어야 합니다.
- 각 인터뷰는 `interview_id`와 `turns`를 포함해야 합니다.
- 각 turn 검증 규칙은 `upsert`와 동일합니다.

## Error Responses

### 400 Bad Request

요청 필수값이 비어 있거나 서비스 레벨 검증에 실패한 경우입니다.

```json
{
  "detail": "interview_id is required."
}
```

### 422 Unprocessable Entity

요청 JSON 구조가 스키마와 맞지 않는 경우입니다.

```json
{
  "detail": [
    {
      "type": "greater_than_equal",
      "loc": ["body", "turns", 0, "turn_index"],
      "msg": "Input should be greater than or equal to 1",
      "input": 0,
      "ctx": {
        "ge": 1
      }
    }
  ]
}
```

## Runtime Dependencies

- 임베딩 서버
- Qdrant
- `Qwen/Qwen3-Embedding-0.6B`

필요 환경변수:

- `DEFAULT_EMBEDDING_MODEL`
- `DEFAULT_EMBEDDING_BASE_URL`
- `DEFAULT_EMBEDDING_API_KEY`
- `DEFAULT_EMBEDDING_TIMEOUT_SECONDS`
- `QDRANT_URL`
- `QDRANT_API_KEY`
- `QDRANT_COLLECTION_NAME`
- `QDRANT_VECTOR_SIZE`
- `QDRANT_TIMEOUT_SECONDS`

## Docker Compose 기준

- FastAPI 내부 임베딩 서버 주소: `http://embedding-server:8000/v1`
- 호스트에서 임베딩 서버 확인 주소: `http://localhost:8001/v1`
- Qdrant 주소: `http://localhost:6333`
