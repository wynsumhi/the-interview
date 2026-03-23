from __future__ import annotations

import json
import sys

import httpx
from qdrant_client import QdrantClient, models


def build_payload() -> dict[str, object]:
    interview_one_title = "디자이너 김하늘 인터뷰"
    interview_two_title = "에세이스트 박서윤 인터뷰"

    return {
        "user_id": "schema-check-user",
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
                        "metadata": {"title": interview_one_title},
                    },
                    {
                        "question_id": "question-002",
                        "answer_id": "answer-002",
                        "question": "작업할 때 가장 중요하게 보는 기준은 무엇인가요?",
                        "answer": (
                            "읽는 사람이 자연스럽게 흐름을 따라올 수 있는 구조를 가장 먼저 봐요."
                        ),
                        "turn_index": 2,
                        "metadata": {"title": interview_one_title},
                    },
                ],
                "metadata": {"title": interview_one_title},
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
                        "metadata": {"title": interview_two_title},
                    }
                ],
                "metadata": {"title": interview_two_title},
            },
        ],
    }


def build_expected_records() -> dict[tuple[str, str, str], dict[str, str]]:
    return {
        ("magazine-interview-001", "question-001", "answer-001"): {
            "question": "최근 작업 중 가장 애착이 가는 프로젝트는 무엇인가요?",
            "answer": "오래 준비한 독립 잡지 리디자인 프로젝트가 가장 기억에 남아요.",
            "title": "디자이너 김하늘 인터뷰",
        },
        ("magazine-interview-001", "question-002", "answer-002"): {
            "question": "작업할 때 가장 중요하게 보는 기준은 무엇인가요?",
            "answer": "읽는 사람이 자연스럽게 흐름을 따라올 수 있는 구조를 가장 먼저 봐요.",
            "title": "디자이너 김하늘 인터뷰",
        },
        ("magazine-interview-002", "question-010", "answer-010"): {
            "question": "요즘 가장 자주 기록하는 소재는 무엇인가요?",
            "answer": "도시에서 마주치는 사소한 장면들을 짧게 메모하고 있어요.",
            "title": "에세이스트 박서윤 인터뷰",
        },
    }


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")

    payload = build_payload()
    expected = build_expected_records()

    response = httpx.post(
        "http://localhost:8000/api/v1/ai/interview/rag/reindex",
        json=payload,
        timeout=180.0,
    )
    print("REINDEX_STATUS", response.status_code)
    print("REINDEX_BODY", response.text)
    response.raise_for_status()

    client = QdrantClient(url="http://localhost:6333", timeout=30.0, prefer_grpc=False)
    points, _ = client.scroll(
        collection_name="interview_history",
        scroll_filter=models.Filter(
            must=[
                models.FieldCondition(
                    key="user_id",
                    match=models.MatchValue(value=str(payload["user_id"])),
                )
            ]
        ),
        limit=10,
        with_payload=True,
    )

    print("POINT_COUNT", len(points))

    all_match = True
    for point in points:
        point_payload = point.payload or {}
        key = (
            str(point_payload.get("interview_id")),
            str(point_payload.get("question_id")),
            str(point_payload.get("answer_id")),
        )
        current = {
            "question": str(point_payload.get("question")),
            "answer": str(point_payload.get("answer")),
            "title": str((point_payload.get("metadata") or {}).get("title")),
        }
        ok = expected.get(key) == current
        all_match = all_match and ok
        print(json.dumps({"key": key, "ok": ok, "stored": current}, ensure_ascii=False))

    print("ALL_MATCH", all_match)
    if not all_match:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
