from __future__ import annotations

from dataclasses import dataclass, field

from tests.rag.qdrant_store import QdrantInterviewStore, QdrantSearchResult
from tests.rag.qwen3_embedding import EmbeddingClient


@dataclass(frozen=True)
class InterviewTurn:
    """이전 인터뷰의 질문과 답변 한 턴을 표현한다."""

    session_id: str
    question: str
    answer: str
    turn_index: int
    metadata: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class RetrievedInterviewTurn:
    """Qdrant 검색 결과를 인터뷰 턴과 함께 전달하기 위한 구조체다."""

    turn: InterviewTurn
    similarity: float
    point_id: str


@dataclass(frozen=True)
class InterviewRAGConfig:
    """인터뷰 RAG의 검색 정책을 테스트 단계에서 명시적으로 관리한다."""

    top_k: int = 3
    score_threshold: float = 0.35
    chunk_size: int | None = None
    chunk_overlap: int = 0
    rerank_enabled: bool = False


def build_turn_document(turn: InterviewTurn) -> str:
    """질문과 답변을 하나의 검색 문서 문자열로 합친다.

    인터뷰에서는 질문과 답변이 분리되면 문맥이 깨지기 쉽다.
    그래서 검색용 문서는 한 턴 전체를 하나의 원자적 문서로 유지한다.
    """

    return (
        f"세션: {turn.session_id}\n"
        f"순번: {turn.turn_index}\n"
        f"질문: {turn.question}\n"
        f"답변: {turn.answer}"
    )


def build_query_text(current_question: str, current_answer: str) -> str:
    """현재 인터뷰 턴을 검색 질의 문자열로 변환한다."""

    return f"현재 질문: {current_question}\n현재 답변: {current_answer}"


def _payload_to_turn(payload: dict[str, object]) -> InterviewTurn:
    """Qdrant payload를 다시 도메인 모델인 InterviewTurn으로 복원한다."""

    return InterviewTurn(
        session_id=str(payload["session_id"]),
        question=str(payload["question"]),
        answer=str(payload["answer"]),
        turn_index=int(payload["turn_index"]),
        metadata=dict(payload.get("metadata", {})),
    )


class InterviewRAGIndex:
    """Qdrant 저장소를 사용해 이전 인터뷰를 검색하는 테스트 전용 인덱스다."""

    def __init__(
        self,
        embedding_client: EmbeddingClient,
        store: QdrantInterviewStore,
        config: InterviewRAGConfig | None = None,
    ) -> None:
        """임베딩 생성기, 저장소, 검색 정책을 받아 인덱스를 초기화한다."""

        self._embedding_client = embedding_client
        self._store = store
        self._config = config or InterviewRAGConfig()

    def add_turns(self, turns: list[InterviewTurn]) -> None:
        """이전 인터뷰 턴들을 실제 Qdrant 저장소에 적재한다.

        인터뷰 한 턴은 길이가 짧고 의미 단위가 명확하므로 별도 청킹을 하지 않는다.
        따라서 `chunk_size=None`, `chunk_overlap=0` 정책을 기본값으로 두고,
        한 턴 전체를 하나의 포인트로 저장한다.
        """

        if not turns:
            return

        documents = [build_turn_document(turn) for turn in turns]
        vectors = self._embedding_client.embed_texts(documents)

        point_ids: list[str] = []
        payloads: list[dict[str, object]] = []
        for turn in turns:
            point_ids.append(f"{turn.session_id}-{turn.turn_index}")
            payloads.append(
                {
                    "session_id": turn.session_id,
                    "question": turn.question,
                    "answer": turn.answer,
                    "turn_index": turn.turn_index,
                    "metadata": turn.metadata,
                }
            )

        self._store.upsert(
            ids=point_ids,
            vectors=vectors,
            payloads=payloads,
        )

    def count(self) -> int:
        """인덱스에 저장된 인터뷰 턴 개수를 반환한다."""

        return self._store.count()

    def retrieve_similar_turns(
        self,
        current_question: str,
        current_answer: str,
        top_k: int | None = None,
        min_similarity: float | None = None,
    ) -> list[RetrievedInterviewTurn]:
        """현재 답변과 유사한 과거 인터뷰 턴을 검색한다."""

        effective_top_k = self._config.top_k if top_k is None else top_k
        effective_threshold = (
            self._config.score_threshold if min_similarity is None else min_similarity
        )

        if self.count() == 0 or effective_top_k <= 0:
            return []

        query_text = build_query_text(current_question, current_answer)
        query_vector = self._embedding_client.embed_texts([query_text])[0]
        search_results: list[QdrantSearchResult] = self._store.search(
            query_vector=query_vector,
            limit=effective_top_k,
            score_threshold=effective_threshold,
        )
        return [
            RetrievedInterviewTurn(
                turn=_payload_to_turn(result.payload),
                similarity=result.score,
                point_id=result.point_id,
            )
            for result in search_results
        ]


def format_retrieved_turns_for_llm(retrieved_turns: list[RetrievedInterviewTurn]) -> str:
    """검색된 과거 인터뷰를 LLM 프롬프트에 붙일 수 있는 문자열로 변환한다."""

    if not retrieved_turns:
        return "참고할 이전 인터뷰 기록이 없습니다."

    blocks: list[str] = []
    for item in retrieved_turns:
        metadata_text = (
            ", ".join(f"{key}={value}" for key, value in sorted(item.turn.metadata.items()))
            or "없음"
        )
        blocks.append(
            "\n".join(
                [
                    f"[유사도 {item.similarity:.4f}]",
                    f"포인트 ID: {item.point_id}",
                    f"세션 ID: {item.turn.session_id}",
                    f"턴 순번: {item.turn.turn_index}",
                    f"이전 질문: {item.turn.question}",
                    f"이전 답변: {item.turn.answer}",
                    f"메타데이터: {metadata_text}",
                ]
            )
        )
    return "\n\n".join(blocks)
