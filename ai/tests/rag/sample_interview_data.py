from __future__ import annotations

from tests.rag.interview_rag import InterviewTurn


def build_sample_interview_turns() -> list[InterviewTurn]:
    """RAG 테스트에 사용할 임시 인터뷰 데이터를 만든다.

    데이터는 검색 주제가 명확히 갈리도록 RAG, 백엔드 성능, 협업 경험을 섞어서 구성한다.
    이렇게 해야 검색 결과의 상위 순위가 의미 있게 검증된다.
    """

    return [
        InterviewTurn(
            session_id="session-rag-001",
            question="이전 프로젝트에서 RAG를 어떻게 설계했나요?",
            answer="Qdrant 기반 벡터 검색과 파이썬 백엔드를 연결해 꼬리질문 품질을 높였습니다.",
            turn_index=1,
            metadata={"topic": "rag", "company": "ssafy"},
        ),
        InterviewTurn(
            session_id="session-rag-002",
            question="검색 품질을 개선하기 위해 어떤 전략을 사용했나요?",
            answer="질문과 답변을 함께 임베딩하고 score threshold를 조절했습니다.",
            turn_index=2,
            metadata={"topic": "rag", "focus": "retrieval"},
        ),
        InterviewTurn(
            session_id="session-backend-001",
            question="백엔드 병목을 해결한 경험을 말해주세요.",
            answer="파이썬 API의 병목 구간을 프로파일링하고 캐시를 도입했습니다.",
            turn_index=3,
            metadata={"topic": "backend", "focus": "performance"},
        ),
        InterviewTurn(
            session_id="session-soft-001",
            question="협업 갈등은 어떻게 해결했나요?",
            answer="역할을 다시 나누고 일정과 책임 범위를 명확히 조정했습니다.",
            turn_index=4,
            metadata={"topic": "communication", "focus": "teamwork"},
        ),
    ]
