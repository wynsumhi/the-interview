from __future__ import annotations

from tests.rag.interview_rag import InterviewTurn


def build_sample_interview_turns() -> list[InterviewTurn]:
    """RAG 테스트에 사용할 매거진 인터뷰 톤의 샘플 데이터를 만든다.

    검색 주제가 선명하게 갈리도록 앨범 작업, 무대 감각, 협업 장면을 섞어 둔다.
    실제 매거진 인터뷰처럼 읽히는 문답을 두고, 닮은 장면이 잘 검색되는지만 본다.
    """

    return [
        InterviewTurn(
            session_id="session-album-001",
            question=("새 앨범을 만들 때 가장 오래 붙들고 있던 감정은 무엇이었나요?"),
            answer=(
                "이번 앨범은 밤의 공기와 혼자 남겨진 시간을 오래 바라보며 만들었어요. "
                "조용한 장면이지만 마음속에서는 가장 큰 파도가 치고 있었죠."
            ),
            turn_index=1,
            metadata={"topic": "album", "mood": "night"},
        ),
        InterviewTurn(
            session_id="session-stage-001",
            question=("무대에 오르기 직전에는 어떤 풍경을 가장 먼저 떠올리나요?"),
            answer=(
                "관객의 표정보다 먼저 조명 냄새와 첫 박자의 진동을 떠올려요. "
                "무대는 늘 긴장되지만, 그 떨림이 저를 가장 또렷하게 만들어 줍니다."
            ),
            turn_index=2,
            metadata={"topic": "stage", "focus": "performance"},
        ),
        InterviewTurn(
            session_id="session-collab-001",
            question="함께 작업하는 사람과 호흡이 잘 맞는 순간은 언제라고 느끼나요?",
            answer=(
                "서로가 말을 많이 하지 않아도 같은 장면을 보고 있다는 확신이 들 때가 있어요. "
                "좋은 협업은 설명보다 리듬이 먼저 맞는 일에 가깝다고 생각합니다."
            ),
            turn_index=3,
            metadata={"topic": "collaboration", "focus": "rhythm"},
        ),
        InterviewTurn(
            session_id="session-routine-001",
            question=("아무도 보지 않는 시간에 스스로를 다잡는 루틴이 있나요?"),
            answer=(
                "아침에 창문을 열고 짧게 산책한 뒤 노트를 펼쳐요. "
                "그렇게 하루의 속도를 조금 늦춰야 제 안의 문장이 들리기 시작하거든요."
            ),
            turn_index=4,
            metadata={"topic": "routine", "focus": "writing"},
        ),
    ]
