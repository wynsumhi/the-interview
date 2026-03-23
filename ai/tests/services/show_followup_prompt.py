#!/usr/bin/env python3
"""꼬리질문 생성 직전의 LLM 프롬프트를 눈으로 확인하는 스크립트다.

사용법:
    python tests/services/show_followup_prompt.py
    python tests/services/show_followup_prompt.py --without-rag
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from app.pipeline.interview_pipeline import _build_regular_messages  # noqa: E402
from app.prompts.interview_prompts import INTERVIEW_SYSTEM_PROMPT  # noqa: E402
from app.services.interview_state_service import InterviewStatePromptPayload  # noqa: E402
from app.services.rag_service import (  # noqa: E402
    RetrievedInterviewTurn,
    format_retrieved_turns_for_prompt,
)


def build_sample_interview_state_prompt() -> InterviewStatePromptPayload:
    return InterviewStatePromptPayload(
        top_state="calm",
        fused_scores={
            "calm": 0.78,
            "tense": 0.12,
            "anxious": 0.05,
            "low": 0.02,
            "engaged": 0.03,
        },
        formatted_prompt=(
            "[인터뷰 상태 참고]\n"
            "다음 점수는 음성 특징과 텍스트를 바탕으로 계산한 참고용 상태 신호입니다.\n"
            "이 값은 진단이 아니라 후속 질문의 강도와 깊이를 조절하기 위한 보조 정보입니다.\n"
            "- top_state: calm\n"
            "- fused_scores: calm=0.78, tense=0.12, anxious=0.05, low=0.02, engaged=0.03"
        ),
    )


def build_sample_retrieved_turns() -> list[RetrievedInterviewTurn]:
    return [
        RetrievedInterviewTurn(
            point_id="user-123:magazine-interview-003:question-002:answer-002",
            similarity=0.9142,
            question="혼자만의 시간을 보낼 때 가장 자주 하는 일이 있나요?",
            answer="밤에 이어폰을 끼고 음악을 들으면 하루가 좀 정리되는 기분이 들어요.",
            turn_index=2,
            interview_id="magazine-interview-003",
            metadata={"title": "늦은 밤의 루틴"},
        ),
        RetrievedInterviewTurn(
            point_id="user-123:magazine-interview-007:question-004:answer-004",
            similarity=0.8731,
            question="스스로를 가장 편안하게 만드는 공간은 어디인가요?",
            answer="집에 돌아와 조명을 조금 어둡게 해두면 그때부터 마음이 풀어져요.",
            turn_index=4,
            interview_id="magazine-interview-007",
            metadata={"title": "집으로 돌아가는 마음"},
        ),
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--without-rag",
        action="store_true",
        help="RAG 참고 블록 없이 기본 프롬프트만 출력한다.",
    )
    args = parser.parse_args()

    prompt_payload = build_sample_interview_state_prompt()
    stt_text = (
        "퇴근하고 집에 돌아와서 조용히 음악 틀어놓는 시간이 제일 편안해요. "
        "그때야 하루가 정리되는 느낌이 들어요."
    )

    rag_context = ""
    if not args.without_rag:
        rag_context = format_retrieved_turns_for_prompt(build_sample_retrieved_turns())

    user_message = _build_regular_messages(
        stt_text=stt_text,
        prompt_payload=prompt_payload,
        rag_context=rag_context,
    )[0]["content"]

    print("=== SYSTEM PROMPT ===")
    print(INTERVIEW_SYSTEM_PROMPT)
    print()
    print("=== USER PROMPT ===")
    print(user_message)


if __name__ == "__main__":
    main()
