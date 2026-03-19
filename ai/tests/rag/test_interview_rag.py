from __future__ import annotations

from tests.rag.interview_rag import (
    InterviewRAGConfig,
    InterviewRAGIndex,
    InterviewTurn,
    build_query_text,
    build_turn_document,
    format_retrieved_turns_for_llm,
)
from tests.rag.qdrant_store import QdrantCollectionConfig, QdrantInterviewStore
from tests.rag.sample_interview_data import build_sample_interview_turns


class StubQwen3EmbeddingClient:
    """키워드 기반의 결정적 벡터를 반환하는 테스트 스텁이다.

    실제 임베딩 모델을 테스트에 직접 붙이면 모델 버전과 실행 환경에 따라 결과가 흔들릴 수 있다.
    그래서 검색 로직 검증은 의미 축을 흉내 낸 고정 벡터 스텁으로 분리한다.
    """

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        """문자열 안의 핵심 키워드 존재 여부를 기반으로 단순 벡터를 만든다."""

        return [self._embed_single_text(text) for text in texts]

    def _embed_single_text(self, text: str) -> list[float]:
        """매거진 인터뷰의 주제 축을 흉내 낸 간단한 벡터를 만든다."""

        normalized_text = text.lower()
        return [
            float(
                "앨범" in normalized_text or "album" in normalized_text or "음악" in normalized_text
            ),
            float(
                "무대" in normalized_text or "stage" in normalized_text or "공연" in normalized_text
            ),
            float(
                "협업" in normalized_text
                or "함께" in normalized_text
                or "collaboration" in normalized_text
            ),
            float(
                "루틴" in normalized_text
                or "아침" in normalized_text
                or "routine" in normalized_text
            ),
            float(
                "밤" in normalized_text or "night" in normalized_text or "조용" in normalized_text
            ),
        ]


def build_test_index(collection_name: str, top_k: int = 3) -> InterviewRAGIndex:
    """실제 qdrant-client 로컬 모드를 사용하는 테스트 인덱스를 만든다."""

    store = QdrantInterviewStore(
        config=QdrantCollectionConfig(
            collection_name=collection_name,
            vector_size=5,
        )
    )
    return InterviewRAGIndex(
        embedding_client=StubQwen3EmbeddingClient(),
        store=store,
        config=InterviewRAGConfig(
            top_k=top_k,
            score_threshold=0.1,
            chunk_size=None,
            chunk_overlap=0,
            rerank_enabled=False,
        ),
    )


def test_build_turn_document_contains_question_and_answer() -> None:
    """검색용 문서가 질문과 답변을 모두 담는지 확인한다."""

    turn = InterviewTurn(
        session_id="session-a",
        question="새 앨범의 첫 문장은 어디에서 시작됐나요?",
        answer="늦은 밤 메모장에 적어 둔 문장에서 시작됐습니다.",
        turn_index=2,
        metadata={"category": "project"},
    )

    document = build_turn_document(turn)

    assert "질문: 새 앨범의 첫 문장은 어디에서 시작됐나요?" in document
    assert "답변: 늦은 밤 메모장에 적어 둔 문장에서 시작됐습니다." in document


def test_interview_rag_index_returns_most_similar_previous_turns() -> None:
    """현재 답변과 의미적으로 가까운 과거 인터뷰가 상위에 오는지 검증한다."""

    index = build_test_index("interview_history_rank_test", top_k=2)
    index.add_turns(
        [
            InterviewTurn(
                session_id="session-a",
                question="이번 앨범에서 가장 오래 남기고 싶었던 감정은 무엇이었나요?",
                answer="밤의 공기와 고요를 오래 붙잡아 두고 싶었습니다.",
                turn_index=1,
                metadata={"topic": "album"},
            ),
            InterviewTurn(
                session_id="session-b",
                question="함께 작업하는 사람과 리듬이 맞는 순간은 언제인가요?",
                answer="설명이 길지 않아도 같은 밤의 장면을 보고 있다는 느낌이 들 때입니다.",
                turn_index=4,
                metadata={"topic": "collaboration"},
            ),
            InterviewTurn(
                session_id="session-c",
                question="무대에 오르기 전 가장 또렷하게 떠오르는 감각은 무엇인가요?",
                answer="첫 박자가 오기 전의 긴장과 조명 냄새를 먼저 떠올립니다.",
                turn_index=3,
                metadata={"topic": "stage"},
            ),
        ]
    )

    retrieved_turns = index.retrieve_similar_turns(
        current_question="이번 작업에서 가장 오래 붙들었던 감정은 무엇이었나요?",
        current_answer="밤의 고요와 혼자 남겨진 시간을 앨범 안에 오래 남기고 싶었습니다.",
    )

    assert len(retrieved_turns) == 2
    assert retrieved_turns[0].turn.session_id == "session-a"
    assert retrieved_turns[1].turn.session_id == "session-b"
    assert retrieved_turns[0].point_id == "session-a-1"
    assert retrieved_turns[0].similarity >= retrieved_turns[1].similarity


def test_format_retrieved_turns_for_llm_creates_readable_context() -> None:
    """검색 결과를 LLM 입력용 문맥 문자열로 안전하게 직렬화하는지 확인한다."""

    index = build_test_index("interview_history_format_test", top_k=1)
    index.add_turns(
        [
            InterviewTurn(
                session_id="session-a",
                question="무대 위의 긴장을 다루는 자신만의 방식이 있나요?",
                answer="첫 박자가 시작되기 전 숨을 길게 고르며 몸의 속도를 맞춥니다.",
                turn_index=1,
                metadata={"topic": "stage", "difficulty": "high"},
            )
        ]
    )

    retrieved_turns = index.retrieve_similar_turns(
        current_question="공연 전에 긴장을 어떻게 다스리나요?",
        current_answer="무대에 오르기 전에는 숨을 길게 고르며 리듬을 맞춥니다.",
    )
    prompt_context = format_retrieved_turns_for_llm(retrieved_turns)

    assert "[유사도 " in prompt_context
    assert "포인트 ID: session-a-1" in prompt_context
    assert "이전 질문: 무대 위의 긴장을 다루는 자신만의 방식이 있나요?" in prompt_context
    assert (
        "이전 답변: 첫 박자가 시작되기 전 숨을 길게 고르며 몸의 속도를 맞춥니다." in prompt_context
    )
    assert "메타데이터: difficulty=high, topic=stage" in prompt_context


def test_build_query_text_keeps_current_question_and_answer_together() -> None:
    """현재 질문과 답변이 하나의 질의 문자열로 합쳐지는지 검증한다."""

    query_text = build_query_text(
        current_question="하루를 시작할 때 꼭 지키는 루틴이 있나요?",
        current_answer="아침 산책을 한 뒤 노트를 펼치며 문장을 천천히 꺼냅니다.",
    )

    assert "현재 질문: 하루를 시작할 때 꼭 지키는 루틴이 있나요?" in query_text
    assert "현재 답변: 아침 산책을 한 뒤 노트를 펼치며 문장을 천천히 꺼냅니다." in query_text


def test_qdrant_shaped_rag_flow_with_sample_data() -> None:
    """임시 인터뷰 데이터를 넣고 Qdrant 검색 흐름이 끝까지 동작하는지 확인한다."""

    sample_turns = build_sample_interview_turns()
    index = build_test_index("interview_history_sample_test", top_k=2)

    index.add_turns(sample_turns)
    retrieved_turns = index.retrieve_similar_turns(
        current_question="무대에 오르기 직전, 이번 앨범의 감정은 어떻게 다시 살아났나요?",
        current_answer="밤의 공기와 첫 박자의 떨림이 올라오면서 무대의 감정이 또렷해졌습니다.",
    )
    prompt_context = format_retrieved_turns_for_llm(retrieved_turns)

    assert index.count() == 4
    assert len(retrieved_turns) == 2
    assert retrieved_turns[0].turn.session_id == "session-album-001"
    assert retrieved_turns[1].turn.session_id == "session-stage-001"
    assert "이번 앨범은 밤의 공기와 혼자 남겨진 시간을 오래 바라보며 만들었어요." in prompt_context


def test_rag_config_disables_chunking_and_rerank_for_interview_turns() -> None:
    """인터뷰 RAG 설정이 한 턴 단위 검색 전략을 유지하는지 검증한다."""

    config = InterviewRAGConfig()

    assert config.top_k == 3
    assert config.score_threshold == 0.35
    assert config.chunk_size is None
    assert config.chunk_overlap == 0
    assert config.rerank_enabled is False
