import pytest
from app.services.rag_service import (
    delete_interview_history_rag,
    format_retrieved_turns_for_prompt,
    reindex_interview_history_rag,
    retrieve_similar_interview_history,
    upsert_interview_history_rag,
)


class StubEmbeddingClient:
    def close(self) -> None:
        return None

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        return [[0.1, 0.2, 0.3] for _ in texts]


class StubQdrantInterviewStore:
    def __init__(self) -> None:
        self.collection_name = "interview_history"
        self.upsert_calls: list[dict[str, object]] = []
        self.delete_interview_calls: list[tuple[str, str]] = []
        self.delete_user_calls: list[str] = []
        self.search_calls: list[dict[str, object]] = []

    def close(self) -> None:
        return None

    def upsert(
        self,
        ids: list[str],
        vectors: list[list[float]],
        payloads: list[dict[str, object]],
    ) -> None:
        self.upsert_calls.append({"ids": ids, "vectors": vectors, "payloads": payloads})

    def delete_by_interview(self, user_id: str, interview_id: str) -> int:
        self.delete_interview_calls.append((user_id, interview_id))
        return 1

    def delete_by_user(self, user_id: str) -> None:
        self.delete_user_calls.append(user_id)

    def search(
        self,
        *,
        user_id: str,
        query_vector: list[float],
        limit: int,
        score_threshold: float,
    ) -> list[object]:
        self.search_calls.append(
            {
                "user_id": user_id,
                "query_vector": query_vector,
                "limit": limit,
                "score_threshold": score_threshold,
            }
        )
        return [
            type(
                "SearchResult",
                (),
                {
                    "point_id": "user-123:magazine-interview-001:question-001:answer-001",
                    "score": 0.91,
                    "payload": {
                        "interview_id": "magazine-interview-001",
                        "question": "이전 질문",
                        "answer": "이전 답변",
                        "turn_index": 1,
                        "metadata": {"title": "이전 인터뷰"},
                    },
                },
            )()
        ]


def build_rag_upsert_request() -> dict[str, object]:
    return {
        "user_id": "user-123",
        "interview_id": "magazine-interview-001",
        "turns": [
            {
                "question_id": "question-001",
                "answer_id": "answer-001",
                "question": "최근 작업 중 가장 애착이 가는 프로젝트는 무엇인가요?",
                "answer": "오래 준비한 독립 잡지 리디자인 프로젝트가 가장 기억에 남아요.",
                "turn_index": 1,
                "metadata": {"title": "디자이너 김하늘 인터뷰"},
            },
            {
                "question_id": "question-002",
                "answer_id": "answer-002",
                "question": "작업할 때 가장 중요하게 보는 기준은 무엇인가요?",
                "answer": "읽는 사람이 자연스럽게 흐름을 따라올 수 있는 구조를 가장 먼저 봐요.",
                "turn_index": 2,
                "metadata": {"title": "디자이너 김하늘 인터뷰"},
            },
        ],
        "metadata": {"title": "디자이너 김하늘 인터뷰"},
    }


def build_rag_delete_request() -> dict[str, object]:
    return {
        "user_id": "user-123",
        "interview_id": "magazine-interview-001",
    }


def build_rag_reindex_request() -> dict[str, object]:
    first_interview = {
        "interview_id": "magazine-interview-001",
        "turns": build_rag_upsert_request()["turns"],
        "metadata": {"title": "디자이너 김하늘 인터뷰"},
    }
    second_interview = {
        "interview_id": "magazine-interview-002",
        "turns": [
            {
                "question_id": "question-010",
                "answer_id": "answer-010",
                "question": "요즘 가장 자주 기록하는 소재는 무엇인가요?",
                "answer": "도시에서 마주치는 사소한 장면들을 짧게 메모하고 있어요.",
                "turn_index": 1,
                "metadata": {"title": "에세이스트 박서윤 인터뷰"},
            }
        ],
        "metadata": {"title": "에세이스트 박서윤 인터뷰"},
    }
    return {
        "user_id": "user-123",
        "interviews": [first_interview, second_interview],
    }


def test_upsert_interview_history_rag_returns_index_summary() -> None:
    store = StubQdrantInterviewStore()
    result = upsert_interview_history_rag(
        build_rag_upsert_request(),
        embedding_client=StubEmbeddingClient(),
        store=store,
    )

    assert result == {
        "status": "completed",
        "message": "Interview history RAG upsert completed.",
        "collection_name": "interview_history",
        "indexed_count": 2,
    }
    assert store.upsert_calls[0]["ids"] == [
        "user-123:magazine-interview-001:question-001:answer-001",
        "user-123:magazine-interview-001:question-002:answer-002",
    ]


def test_upsert_interview_history_rag_rejects_empty_turns() -> None:
    request = build_rag_upsert_request()
    request["turns"] = []

    with pytest.raises(ValueError, match="turns must contain at least one interview turn."):
        upsert_interview_history_rag(
            request,
            embedding_client=StubEmbeddingClient(),
            store=StubQdrantInterviewStore(),
        )


def test_delete_interview_history_rag_returns_delete_summary() -> None:
    store = StubQdrantInterviewStore()
    result = delete_interview_history_rag(build_rag_delete_request(), store=store)

    assert result == {
        "status": "completed",
        "message": "Interview history RAG delete completed.",
        "collection_name": "interview_history",
        "deleted_count": 1,
    }
    assert store.delete_interview_calls == [("user-123", "magazine-interview-001")]


def test_delete_interview_history_rag_requires_interview_id() -> None:
    request = build_rag_delete_request()
    request["interview_id"] = " "

    with pytest.raises(ValueError, match="interview_id is required."):
        delete_interview_history_rag(request, store=StubQdrantInterviewStore())


def test_reindex_interview_history_rag_returns_reindex_summary() -> None:
    store = StubQdrantInterviewStore()
    result = reindex_interview_history_rag(
        build_rag_reindex_request(),
        embedding_client=StubEmbeddingClient(),
        store=store,
    )

    assert result == {
        "status": "completed",
        "message": "Interview history RAG reindex completed.",
        "collection_name": "interview_history",
        "reindexed_interview_count": 2,
        "reindexed_turn_count": 3,
    }
    assert store.delete_user_calls == ["user-123"]
    assert len(store.upsert_calls) == 2


def test_reindex_interview_history_rag_requires_interviews() -> None:
    request = build_rag_reindex_request()
    request["interviews"] = []

    with pytest.raises(ValueError, match="interviews must contain at least one interview."):
        reindex_interview_history_rag(
            request,
            embedding_client=StubEmbeddingClient(),
            store=StubQdrantInterviewStore(),
        )


def test_retrieve_similar_interview_history_returns_search_results() -> None:
    store = StubQdrantInterviewStore()

    result = retrieve_similar_interview_history(
        user_id="user-123",
        current_question="현재 질문",
        current_answer="현재 답변",
        embedding_client=StubEmbeddingClient(),
        store=store,
    )

    assert len(result) == 1
    assert result[0].interview_id == "magazine-interview-001"
    assert result[0].question == "이전 질문"
    assert store.search_calls == [
        {
            "user_id": "user-123",
            "query_vector": [0.1, 0.2, 0.3],
            "limit": 3,
            "score_threshold": 0.35,
        }
    ]


def test_format_retrieved_turns_for_prompt_returns_empty_string_for_no_results() -> None:
    assert format_retrieved_turns_for_prompt([]) == ""


def test_format_retrieved_turns_for_prompt_creates_llm_context() -> None:
    retrieved_turns = retrieve_similar_interview_history(
        user_id="user-123",
        current_question="현재 질문",
        current_answer="현재 답변",
        embedding_client=StubEmbeddingClient(),
        store=StubQdrantInterviewStore(),
    )

    result = format_retrieved_turns_for_prompt(retrieved_turns)

    assert "[Similarity 0.9100]" in result
    assert "Previous Question: 이전 질문" in result
    assert "Previous Answer: 이전 답변" in result
