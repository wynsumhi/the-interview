from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.models.rag import (
    EmbeddingClient,
    OpenAIEmbeddingClient,
    QdrantInterviewStore,
    QdrantSearchResult,
)

DEFAULT_HISTORY_RAG_TOP_K = 3
DEFAULT_HISTORY_RAG_SCORE_THRESHOLD = 0.35


@dataclass(frozen=True)
class RetrievedInterviewTurn:
    """파이프라인 프롬프트에 넣을 과거 인터뷰 검색 결과다."""

    point_id: str
    similarity: float
    question: str
    answer: str
    turn_index: int
    interview_id: str
    metadata: dict[str, Any]


def _normalize_turns(turns: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """문답 목록을 검증하고 서비스 내부 형식으로 정규화한다."""

    if not turns:
        raise ValueError("turns must contain at least one interview turn.")

    normalized_turns: list[dict[str, Any]] = []
    for turn in turns:
        question_id = str(turn.get("question_id", "")).strip()
        answer_id = str(turn.get("answer_id", "")).strip()
        question = str(turn.get("question", "")).strip()
        answer = str(turn.get("answer", "")).strip()
        turn_index = int(turn.get("turn_index", 0))

        if not question_id:
            raise ValueError("Each turn must include a non-empty question_id.")
        if not answer_id:
            raise ValueError("Each turn must include a non-empty answer_id.")
        if not question:
            raise ValueError("Each turn must include a non-empty question.")
        if not answer:
            raise ValueError("Each turn must include a non-empty answer.")
        if turn_index < 1:
            raise ValueError("Each turn must include turn_index greater than or equal to 1.")

        normalized_turns.append(
            {
                "question_id": question_id,
                "answer_id": answer_id,
                "question": question,
                "answer": answer,
                "turn_index": turn_index,
                "metadata": dict(turn.get("metadata", {})),
            }
        )

    return normalized_turns


def _require_field(request: dict[str, Any], key: str) -> str:
    """필수 문자열 필드를 검증하고 반환한다."""

    value = str(request.get(key, "")).strip()
    if not value:
        raise ValueError(f"{key} is required.")
    return value


def _build_turn_document(question: str, answer: str) -> str:
    """질문과 답변을 임베딩 입력 문자열로 합친다."""

    return f"Question: {question}\nAnswer: {answer}"


def _build_query_text(current_question: str, current_answer: str) -> str:
    """현재 질문과 답변을 묶어 유사 히스토리 검색 질의문으로 만든다."""

    return f"Current Question: {current_question}\nCurrent Answer: {current_answer}"


def _to_retrieved_turn(result: QdrantSearchResult) -> RetrievedInterviewTurn:
    payload = result.payload
    return RetrievedInterviewTurn(
        point_id=result.point_id,
        similarity=result.score,
        question=str(payload.get("question", "")),
        answer=str(payload.get("answer", "")),
        turn_index=int(payload.get("turn_index", 0)),
        interview_id=str(payload.get("interview_id", "")),
        metadata=dict(payload.get("metadata", {})),
    )


def _build_index_rows(
    *,
    user_id: str,
    interview_id: str,
    turns: list[dict[str, Any]],
    metadata: dict[str, str],
) -> tuple[list[str], list[str], list[dict[str, Any]]]:
    """Qdrant upsert에 필요한 point id, 문서, payload를 생성한다."""

    point_ids = [
        f"{user_id}:{interview_id}:{turn['question_id']}:{turn['answer_id']}" for turn in turns
    ]
    documents = [_build_turn_document(turn["question"], turn["answer"]) for turn in turns]
    payloads = [
        {
            "user_id": user_id,
            "interview_id": interview_id,
            "question_id": turn["question_id"],
            "answer_id": turn["answer_id"],
            "question": turn["question"],
            "answer": turn["answer"],
            "turn_index": turn["turn_index"],
            "metadata": {**metadata, **turn["metadata"]},
        }
        for turn in turns
    ]
    return point_ids, documents, payloads


def _close_resource(resource: Any) -> None:
    """close 메서드가 있으면 안전하게 정리한다."""

    close = getattr(resource, "close", None)
    if callable(close):
        close()


def retrieve_similar_interview_history(
    *,
    user_id: str,
    current_question: str,
    current_answer: str,
    top_k: int = DEFAULT_HISTORY_RAG_TOP_K,
    score_threshold: float = DEFAULT_HISTORY_RAG_SCORE_THRESHOLD,
    embedding_client: EmbeddingClient | None = None,
    store: QdrantInterviewStore | None = None,
) -> list[RetrievedInterviewTurn]:
    """현재 답변과 유사한 사용자의 과거 인터뷰 문답을 검색한다."""

    normalized_user_id = user_id.strip()
    normalized_question = current_question.strip()
    normalized_answer = current_answer.strip()
    if not normalized_user_id or not normalized_question or not normalized_answer or top_k <= 0:
        return []

    embedding_client = embedding_client or OpenAIEmbeddingClient()
    store = store or QdrantInterviewStore()
    try:
        query_text = _build_query_text(normalized_question, normalized_answer)
        query_vector = embedding_client.embed_texts([query_text])[0]
        search_results = store.search(
            user_id=normalized_user_id,
            query_vector=query_vector,
            limit=top_k,
            score_threshold=score_threshold,
        )
        return [_to_retrieved_turn(result) for result in search_results]
    finally:
        _close_resource(embedding_client)
        _close_resource(store)


def format_retrieved_turns_for_prompt(retrieved_turns: list[RetrievedInterviewTurn]) -> str:
    """검색된 과거 히스토리를 꼬리질문 생성 프롬프트용 문자열로 정리한다."""

    if not retrieved_turns:
        return ""

    blocks: list[str] = []
    for item in retrieved_turns:
        metadata_text = (
            ", ".join(f"{key}={value}" for key, value in sorted(item.metadata.items())) or "none"
        )
        blocks.append(
            "\n".join(
                [
                    f"[Similarity {item.similarity:.4f}]",
                    f"Interview ID: {item.interview_id}",
                    f"Turn Index: {item.turn_index}",
                    f"Previous Question: {item.question}",
                    f"Previous Answer: {item.answer}",
                    f"Metadata: {metadata_text}",
                ]
            )
        )
    return "\n\n".join(blocks)


def upsert_interview_history_rag(
    request: dict[str, Any],
    embedding_client: EmbeddingClient | None = None,
    store: QdrantInterviewStore | None = None,
) -> dict[str, Any]:
    """단일 인터뷰 문답을 사용자 RAG에 추가 적재한다."""

    user_id = _require_field(request, "user_id")
    interview_id = _require_field(request, "interview_id")
    normalized_turns = _normalize_turns(request.get("turns") or [])
    metadata = dict(request.get("metadata", {}))

    embedding_client = embedding_client or OpenAIEmbeddingClient()
    store = store or QdrantInterviewStore()
    try:
        point_ids, documents, payloads = _build_index_rows(
            user_id=user_id,
            interview_id=interview_id,
            turns=normalized_turns,
            metadata=metadata,
        )
        vectors = embedding_client.embed_texts(documents)
        store.upsert(ids=point_ids, vectors=vectors, payloads=payloads)
        return {
            "status": "completed",
            "message": "Interview history RAG upsert completed.",
            "collection_name": store.collection_name,
            "indexed_count": len(normalized_turns),
        }
    finally:
        _close_resource(embedding_client)
        _close_resource(store)


def delete_interview_history_rag(
    request: dict[str, Any],
    store: QdrantInterviewStore | None = None,
) -> dict[str, Any]:
    """특정 인터뷰를 사용자 RAG에서 삭제한다."""

    user_id = _require_field(request, "user_id")
    interview_id = _require_field(request, "interview_id")

    store = store or QdrantInterviewStore()
    try:
        deleted_count = store.delete_by_interview(user_id=user_id, interview_id=interview_id)
        return {
            "status": "completed",
            "message": "Interview history RAG delete completed.",
            "collection_name": store.collection_name,
            "deleted_count": deleted_count,
        }
    finally:
        _close_resource(store)


def reindex_interview_history_rag(
    request: dict[str, Any],
    embedding_client: EmbeddingClient | None = None,
    store: QdrantInterviewStore | None = None,
) -> dict[str, Any]:
    """사용자의 전체 인터뷰 이력을 기준으로 RAG를 다시 구성한다."""

    user_id = _require_field(request, "user_id")
    interviews = request.get("interviews") or []
    if not interviews:
        raise ValueError("interviews must contain at least one interview.")

    store = store or QdrantInterviewStore()
    embedding_client = embedding_client or OpenAIEmbeddingClient()
    try:
        store.delete_by_user(user_id=user_id)

        total_turns = 0
        for interview in interviews:
            interview_request = dict(interview)
            normalized_turns = _normalize_turns(interview_request.get("turns") or [])
            interview_id = _require_field(interview_request, "interview_id")
            metadata = dict(interview_request.get("metadata", {}))

            point_ids, documents, payloads = _build_index_rows(
                user_id=user_id,
                interview_id=interview_id,
                turns=normalized_turns,
                metadata=metadata,
            )
            vectors = embedding_client.embed_texts(documents)
            store.upsert(ids=point_ids, vectors=vectors, payloads=payloads)
            total_turns += len(normalized_turns)

        return {
            "status": "completed",
            "message": "Interview history RAG reindex completed.",
            "collection_name": store.collection_name,
            "reindexed_interview_count": len(interviews),
            "reindexed_turn_count": total_turns,
        }
    finally:
        _close_resource(embedding_client)
        _close_resource(store)
