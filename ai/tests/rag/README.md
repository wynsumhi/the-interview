# RAG Test Result

## 개요

이 문서는 `ai/tests/rag`에 구현한 테스트 전용 RAG의 실행 결과와 현재 설정을 정리한 문서다.
현재 구현은 `app`으로 옮기지 않고, `tests` 내부에서만 동작하도록 구성되어 있다.

- 임베딩 모델 인터페이스: `qwen3` 호환 임베딩 클라이언트 구조
- 벡터 저장소: `qdrant-client==1.15.1`
- 실행 방식: `QdrantClient(location=":memory:")`
- 검증 환경: 기존 `ai/venv`

## 실행 명령

아래 명령으로 실제 테스트를 실행했다.

```powershell
venv\Scripts\python -m pytest tests\rag -q
venv\Scripts\python -m ruff check tests\rag
```

## 실행 결과

### pytest

```text
........                                                                 [100%]
8 passed in 2.18s
```

### ruff

```text
All checks passed!
```

## 샘플 데이터 검색 결과

임시 인터뷰 데이터를 넣고 아래 질의로 검색했다.

- 현재 질문: `검색 품질을 높이기 위해 어떤 retrieval 전략을 썼나요?`
- 현재 답변: `Qdrant 기반 RAG에서 질문과 답변을 함께 임베딩하고 score threshold를 조절했습니다.`

검색 상위 결과는 아래와 같았다.

```text
session-rag-002-2 session-rag-002 0.7071
session-rag-001-1 session-rag-001 0.6325
```

LLM에 전달할 문맥 문자열은 아래처럼 생성된다.

```text
[유사도 0.7071]
포인트 ID: session-rag-002-2
세션 ID: session-rag-002
턴 순번: 2
이전 질문: 검색 품질을 개선하기 위해 어떤 전략을 사용했나요?
이전 답변: 질문과 답변을 함께 임베딩하고 score threshold를 조절했습니다.
메타데이터: focus=retrieval, topic=rag

[유사도 0.6325]
포인트 ID: session-rag-001-1
세션 ID: session-rag-001
턴 순번: 1
이전 질문: 이전 프로젝트에서 RAG를 어떻게 설계했나요?
이전 답변: Qdrant 기반 벡터 검색과 파이썬 백엔드를 연결해 꼬리질문 품질을 높였습니다.
메타데이터: company=ssafy, topic=rag
```

## 현재 파일 역할

- `tests/rag/qwen3_embedding.py`
  `qwen3` 임베딩 호출 인터페이스와 OpenAI 호환 `/embeddings` 요청 클라이언트를 제공한다.
- `tests/rag/qdrant_store.py`
  실제 `qdrant-client`를 사용해 컬렉션 생성, 포인트 적재, 검색을 수행한다.
- `tests/rag/interview_rag.py`
  인터뷰 턴 문서화, 질의 생성, Qdrant 검색, LLM 문맥 포맷팅을 담당한다.
- `tests/rag/sample_interview_data.py`
  RAG 검색 검증용 임시 인터뷰 데이터를 제공한다.
- `tests/rag/test_qwen3_embedding.py`
  임베딩 요청 형식과 입력 검증을 테스트한다.
- `tests/rag/test_interview_rag.py`
  인터뷰 검색 순위, 컨텍스트 생성, 샘플 데이터 기반 end-to-end 흐름을 테스트한다.

## RAG 설정

현재 설정은 `tests/rag/interview_rag.py`의 `InterviewRAGConfig` 기준이다.

- `top_k=3`
  기본 검색 개수다. 너무 많은 과거 문맥을 넣으면 꼬리질문 생성 시 노이즈가 커질 수 있어 작게 잡았다.
- `score_threshold=0.35`
  기본 유사도 하한선이다. 관련성이 낮은 인터뷰 턴을 기본적으로 제외하기 위한 값이다.
- `chunk_size=None`
  인터뷰는 질문과 답변 한 턴이 하나의 의미 단위라서 문서를 쪼개지 않는다.
- `chunk_overlap=0`
  청킹을 사용하지 않으므로 overlap도 0으로 둔다.
- `rerank_enabled=False`
  현재는 1차 벡터 검색만 검증하는 단계라서 reranker는 넣지 않았다.

## 왜 이렇게 설정했는가

일반 문서 RAG와 달리 인터뷰 이력은 텍스트 길이가 짧고, 질문과 답변이 함께 있어야 의미가 유지된다.
그래서 `chunk_size`, `overlap` 중심 설계보다 "한 인터뷰 턴을 하나의 문서로 저장"하는 전략이 더 적합하다고 봤다.

또한 지금 단계의 목적은 다음 두 가지다.

1. 이전 인터뷰 이력 검색이 실제로 유사한 턴을 찾아오는지 검증한다.
2. 검색 결과를 다음 꼬리질문 생성용 프롬프트 문맥으로 안정적으로 변환하는지 검증한다.

이 목적에는 rerank까지 넣는 것보다 먼저 Qdrant 검색 결과와 프롬프트 품질을 확인하는 편이 더 적절하다.
실서비스 단계에서 검색 대상이 커지면 그때 cross-encoder rerank나 metadata filter를 추가하는 것이 맞다.

## 참고 사항

- 현재 테스트는 `qwen3` 실제 임베딩 서버를 호출하지 않는다.
- 임베딩 검색 로직 검증은 결정적 결과를 위해 테스트 스텁 임베더를 사용한다.
- 실제 서비스에 붙일 때는 `qwen3` 임베딩 벡터 차원에 맞춰 `vector_size`를 조정해야 한다.
- Qdrant 로컬 모드는 문자열 포인트 ID에 제약이 있어, 내부 저장 ID는 정수 해시를 사용하고 사람이 읽는 포인트 ID는 payload에 유지한다.
