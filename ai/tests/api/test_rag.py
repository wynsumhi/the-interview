from unittest.mock import patch

from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)


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
            }
        ],
        "metadata": {"title": "디자이너 김하늘 인터뷰"},
    }


def build_rag_reindex_request() -> dict[str, object]:
    return {
        "user_id": "user-123",
        "interviews": [
            {
                "interview_id": "magazine-interview-001",
                "turns": build_rag_upsert_request()["turns"],
                "metadata": {"title": "디자이너 김하늘 인터뷰"},
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
                        "metadata": {"title": "에세이스트 박서윤 인터뷰"},
                    }
                ],
                "metadata": {"title": "에세이스트 박서윤 인터뷰"},
            },
        ],
    }


def test_rag_upsert_accepts_new_interview_history_request() -> None:
    with patch(
        "app.api.v1.endpoints.rag.upsert_interview_history_rag",
        return_value={
            "status": "completed",
            "message": "Interview history RAG upsert completed.",
            "collection_name": "interview_history",
            "indexed_count": 1,
        },
    ):
        response = client.post("/api/v1/ai/interview/rag/upsert", json=build_rag_upsert_request())

    assert response.status_code == 200
    assert response.json()["indexed_count"] == 1


def test_rag_delete_accepts_interview_delete_request() -> None:
    with patch(
        "app.api.v1.endpoints.rag.delete_interview_history_rag",
        return_value={
            "status": "completed",
            "message": "Interview history RAG delete completed.",
            "collection_name": "interview_history",
            "deleted_count": 1,
        },
    ):
        response = client.request(
            "DELETE",
            "/api/v1/ai/interview/rag/delete",
            json={"user_id": "user-123", "interview_id": "magazine-interview-001"},
        )

    assert response.status_code == 200
    assert response.json()["deleted_count"] == 1


def test_rag_reindex_accepts_full_interview_request() -> None:
    with patch(
        "app.api.v1.endpoints.rag.reindex_interview_history_rag",
        return_value={
            "status": "completed",
            "message": "Interview history RAG reindex completed.",
            "collection_name": "interview_history",
            "reindexed_interview_count": 2,
            "reindexed_turn_count": 2,
        },
    ):
        response = client.post("/api/v1/ai/interview/rag/reindex", json=build_rag_reindex_request())

    assert response.status_code == 200
    assert response.json()["reindexed_interview_count"] == 2


def test_rag_upsert_returns_unprocessable_entity_for_invalid_turn_index() -> None:
    request = build_rag_upsert_request()
    request["turns"][0]["turn_index"] = 0

    response = client.post("/api/v1/ai/interview/rag/upsert", json=request)

    assert response.status_code == 422
