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
        """기술 주제별 키워드를 세어 유사도 비교 가능한 벡터로 바꾼다."""

        normalized_text = text.lower()
        return [
            float("rag" in normalized_text or "검색" in normalized_text),
            float("vector" in normalized_text or "벡터" in normalized_text),
            float("backend" in normalized_text or "백엔드" in normalized_text),
            float("python" in normalized_text or "파이썬" in normalized_text),
            float("qdrant" in normalized_text),
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
        question="RAG를 프로젝트에 어떻게 적용했나요?",
        answer="벡터 검색을 이용해 유사한 문서를 참고했습니다.",
        turn_index=2,
        metadata={"category": "project"},
    )

    document = build_turn_document(turn)

    assert "질문: RAG를 프로젝트에 어떻게 적용했나요?" in document
    assert "답변: 벡터 검색을 이용해 유사한 문서를 참고했습니다." in document


def test_interview_rag_index_returns_most_similar_previous_turns() -> None:
    """현재 답변과 의미적으로 가까운 과거 인터뷰가 상위에 오는지 검증한다."""

    index = build_test_index("interview_history_rank_test", top_k=2)
    index.add_turns(
        [
            InterviewTurn(
                session_id="session-a",
                question="RAG를 적용한 경험이 있나요?",
                answer="벡터 검색과 파이썬 백엔드로 구현했습니다.",
                turn_index=1,
                metadata={"topic": "rag"},
            ),
            InterviewTurn(
                session_id="session-b",
                question="협업 중 갈등을 어떻게 해결했나요?",
                answer="일정을 조율하고 책임 범위를 다시 정리했습니다.",
                turn_index=4,
                metadata={"topic": "soft-skill"},
            ),
            InterviewTurn(
                session_id="session-c",
                question="백엔드 성능을 개선한 경험이 있나요?",
                answer="파이썬 API 병목을 분석하고 캐시를 도입했습니다.",
                turn_index=3,
                metadata={"topic": "backend"},
            ),
        ]
    )

    retrieved_turns = index.retrieve_similar_turns(
        current_question="이전 프로젝트에서 어떤 검색 시스템을 만들었나요?",
        current_answer="RAG 기반 벡터 검색을 파이썬 백엔드에 연결했습니다.",
    )

    assert len(retrieved_turns) == 2
    assert retrieved_turns[0].turn.session_id == "session-a"
    assert retrieved_turns[1].turn.session_id == "session-c"
    assert retrieved_turns[0].point_id == "session-a-1"
    assert retrieved_turns[0].similarity >= retrieved_turns[1].similarity


def test_format_retrieved_turns_for_llm_creates_readable_context() -> None:
    """검색 결과를 LLM 입력용 문맥 문자열로 안전하게 직렬화하는지 확인한다."""

    index = build_test_index("interview_history_format_test", top_k=1)
    index.add_turns(
        [
            InterviewTurn(
                session_id="session-a",
                question="RAG 구축 경험을 설명해주세요.",
                answer="검색 품질을 위해 벡터 인덱스를 운영했습니다.",
                turn_index=1,
                metadata={"topic": "rag", "difficulty": "high"},
            )
        ]
    )

    retrieved_turns = index.retrieve_similar_turns(
        current_question="비슷한 검색 시스템 경험이 있나요?",
        current_answer="벡터 검색과 RAG를 직접 구현했습니다.",
    )
    prompt_context = format_retrieved_turns_for_llm(retrieved_turns)

    assert "[유사도 " in prompt_context
    assert "포인트 ID: session-a-1" in prompt_context
    assert "이전 질문: RAG 구축 경험을 설명해주세요." in prompt_context
    assert "이전 답변: 검색 품질을 위해 벡터 인덱스를 운영했습니다." in prompt_context
    assert "메타데이터: difficulty=high, topic=rag" in prompt_context


def test_build_query_text_keeps_current_question_and_answer_together() -> None:
    """현재 질문과 답변이 하나의 질의 문자열로 합쳐지는지 검증한다."""

    query_text = build_query_text(
        current_question="벡터 검색을 왜 사용했나요?",
        current_answer="유사한 인터뷰 답변을 찾아 꼬리질문 품질을 높이기 위해서입니다.",
    )

    assert "현재 질문: 벡터 검색을 왜 사용했나요?" in query_text
    assert "현재 답변: 유사한 인터뷰 답변을 찾아 꼬리질문 품질을 높이기 위해서입니다." in query_text


def test_qdrant_shaped_rag_flow_with_sample_data() -> None:
    """임시 인터뷰 데이터를 넣고 Qdrant 검색 흐름이 끝까지 동작하는지 확인한다."""

    sample_turns = build_sample_interview_turns()
    index = build_test_index("interview_history_sample_test", top_k=2)

    index.add_turns(sample_turns)
    retrieved_turns = index.retrieve_similar_turns(
        current_question="검색 품질을 높이기 위해 어떤 retrieval 전략을 썼나요?",
        current_answer=(
            "Qdrant 기반 RAG에서 질문과 답변을 함께 임베딩하고 score threshold를 조절했습니다."
        ),
    )
    prompt_context = format_retrieved_turns_for_llm(retrieved_turns)

    assert index.count() == 4
    assert len(retrieved_turns) == 2
    assert retrieved_turns[0].turn.session_id == "session-rag-002"
    assert retrieved_turns[1].turn.session_id == "session-rag-001"
    assert (
        "Qdrant 기반 벡터 검색과 파이썬 백엔드를 연결해 꼬리질문 품질을 높였습니다."
        in prompt_context
    )


def test_rag_config_disables_chunking_and_rerank_for_interview_turns() -> None:
    """인터뷰 RAG 설정이 한 턴 단위 검색 전략을 유지하는지 검증한다."""

    config = InterviewRAGConfig()

    assert config.top_k == 3
    assert config.score_threshold == 0.35
    assert config.chunk_size is None
    assert config.chunk_overlap == 0
    assert config.rerank_enabled is False
