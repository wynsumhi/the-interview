#!/usr/bin/env python3
"""
더미 데이터 인터뷰 생성 스크립트

STT/TTS 없이 텍스트 입력으로 실제 서비스 흐름과 동일하게 인터뷰를 진행합니다.
완료된 인터뷰는 tests/result/interview_{n}.md 파일로 저장됩니다.

사용법:
    python tests/services/run_dummy_interviews.py

인터뷰 종료 (2회차 이후):
    답변 입력 시 'done' 또는 'end' 입력
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[2] / ".env")

from app.prompts.interview_prompts import (  # noqa: E402
    FIRST_INTERVIEW_QUESTION_MAP,
    FIRST_INTERVIEW_SYSTEM_PROMPT,
    INTERVIEW_SYSTEM_PROMPT,
    NEXT_OPENING_SYSTEM_PROMPT,
    TITLE_GENERATION_SYSTEM_PROMPT,
)
from app.services.llm_service import create_message  # noqa: E402

RESULT_DIR = Path(__file__).resolve().parents[1] / "result"
SEPARATOR = "=" * 60

# 1회차 최초 인터뷰 시작 시 사용하는 하드코딩 인사말 (interview_service.py와 동일)
NEW_USER_START_QUESTION_TEMPLATE = (
    "안녕하세요. {nickname}님. 오늘 시간 내주셔서 감사해요. 요즘 어떻게 지내고 계시나요?"
)


# ---------------------------------------------------------------------------
# 저장
# ---------------------------------------------------------------------------


def save_interview_md(interview_num: int, title: str, qa_pairs: list[dict]) -> Path:
    RESULT_DIR.mkdir(parents=True, exist_ok=True)
    filepath = RESULT_DIR / f"interview_{interview_num}.md"

    lines = [
        f"# {title}",
        "",
        f"*인터뷰 {interview_num}회차*",
        "",
        "---",
        "",
    ]
    for i, qa in enumerate(qa_pairs, 1):
        lines += [
            f"**Q{i}.** {qa['question']}",
            "",
            f"**A.** {qa['answer']}",
            "",
        ]

    filepath.write_text("\n".join(lines), encoding="utf-8")
    return filepath


# ---------------------------------------------------------------------------
# LLM 호출 헬퍼
# ---------------------------------------------------------------------------


def build_interview_context_text(qa_pairs: list[dict]) -> str:
    lines = []
    for i, qa in enumerate(qa_pairs, 1):
        lines.append(f"Q{i}: {qa['question']}")
        lines.append(f"A{i}: {qa['answer']}")
        lines.append("")
    return "\n".join(lines).strip()


def generate_title(interview_context: str) -> str:
    return create_message(
        messages=[{"role": "user", "content": interview_context}],
        system=TITLE_GENERATION_SYSTEM_PROMPT,
        max_tokens=64,
    ).strip()


def generate_next_opening(interview_context: str, nickname: str) -> str:
    nickname_line = f"사용자 닉네임: {nickname}\n\n" if nickname.strip() else ""
    user_message = f"{nickname_line}이전 인터뷰 내용:\n{interview_context}"
    return create_message(
        messages=[{"role": "user", "content": user_message}],
        system=NEXT_OPENING_SYSTEM_PROMPT,
        max_tokens=256,
    ).strip()


def _finalize_interview(
    interview_num: int, nickname: str, qa_pairs: list[dict]
) -> tuple[list[dict], str, str]:
    """인터뷰 종료 후 제목·다음 첫 질문 생성 및 MD 저장"""
    if not qa_pairs:
        print("Q&A가 없어 저장하지 않습니다.")
        return qa_pairs, "", ""

    interview_context = build_interview_context_text(qa_pairs)

    print("\n[제목 생성 중...]")
    title = generate_title(interview_context)
    print(f"제목: {title}")

    print("\n[다음 회차 첫 질문 생성 중...]")
    next_opening = generate_next_opening(interview_context, nickname)
    print(f"다음 첫 질문: {next_opening}")

    save_path = save_interview_md(interview_num, title, qa_pairs)
    print(f"\n저장 완료: {save_path}")

    return qa_pairs, title, next_opening


# ---------------------------------------------------------------------------
# 인터뷰 진행
# ---------------------------------------------------------------------------


def get_answer(prompt: str = "나: ") -> str:
    try:
        return input(f"\n{prompt}").strip()
    except EOFError:
        return "end"


def run_first_interview(interview_num: int, nickname: str) -> tuple[list[dict], str, str]:
    """1회차 최초 인터뷰 — FIRST_INTERVIEW_SYSTEM_PROMPT 사용 (5문항 고정)"""
    print(f"\n{SEPARATOR}")
    print(f" 인터뷰 {interview_num}회차 (최초 인터뷰)")
    print(SEPARATOR)

    qa_pairs = []

    # 하드코딩 인사말 — 실제 서비스와 동일하게 시작
    start_question = NEW_USER_START_QUESTION_TEMPLATE.format(nickname=nickname)
    print(f"\n인터뷰어: {start_question}")
    start_answer = get_answer()

    # 인사말 + 답변도 Q&A로 기록 (컨텍스트에 포함)
    qa_pairs.append({"question": start_question, "answer": start_answer})

    previous_question: str | None = None
    previous_answer: str | None = None

    for sequence in range(1, 6):
        # 실제 파이프라인과 동일한 유저 메시지 구성
        if sequence == 1:
            context = "첫 질문이므로 이전 대화 없음."
        else:
            context = f"직전 질문:\n{previous_question}\n\n사용자 답변:\n{previous_answer}"

        user_message = (
            f"사용자 닉네임: {nickname}\n"
            f"현재 진행할 질문: {FIRST_INTERVIEW_QUESTION_MAP[sequence]}\n\n"
            f"대화 맥락:\n{context}"
        )

        question = create_message(
            messages=[{"role": "user", "content": user_message}],
            system=FIRST_INTERVIEW_SYSTEM_PROMPT,
        ).strip()

        print(f"\n[{sequence}/5] 인터뷰어: {question}")

        answer = get_answer()
        qa_pairs.append({"question": question, "answer": answer})

        previous_question = question
        previous_answer = answer

    return _finalize_interview(interview_num, nickname, qa_pairs)


def run_followup_interview(
    interview_num: int, nickname: str, opening_question: str
) -> tuple[list[dict], str, str]:
    """2회차 이후 인터뷰 — INTERVIEW_SYSTEM_PROMPT 기반 꼬리 질문 진행"""
    print(f"\n{SEPARATOR}")
    print(f" 인터뷰 {interview_num}회차")
    print(SEPARATOR)
    print("인터뷰 종료: 'done' 또는 'end' 입력")

    qa_pairs: list[dict] = []
    # Claude API는 첫 메시지가 user여야 하므로, assistant 역할로 첫 질문을 넣어 대화 흐름 유지
    messages: list[dict] = [{"role": "assistant", "content": opening_question}]
    current_question = opening_question

    print(f"\n인터뷰어: {opening_question}")

    while True:
        answer = get_answer()

        if answer.lower() in ("done", "end"):
            print("인터뷰를 종료합니다.")
            break

        qa_pairs.append({"question": current_question, "answer": answer})
        messages.append({"role": "user", "content": answer})

        next_question = create_message(
            messages=messages,
            system=INTERVIEW_SYSTEM_PROMPT,
        ).strip()

        messages.append({"role": "assistant", "content": next_question})
        current_question = next_question
        print(f"\n인터뷰어: {next_question}")

    return _finalize_interview(interview_num, nickname, qa_pairs)


# ---------------------------------------------------------------------------
# 메인
# ---------------------------------------------------------------------------


def main() -> None:
    print("=== 더미 데이터 인터뷰 생성기 ===")
    nickname = input("닉네임을 입력하세요: ").strip() or "사용자"

    next_opening: str | None = None
    interview_num = 1

    while True:
        if interview_num == 1:
            _, _, next_opening = run_first_interview(interview_num, nickname)
        else:
            assert next_opening, "다음 회차 첫 질문이 없습니다."
            _, _, next_opening = run_followup_interview(interview_num, nickname, next_opening)

        print(f"\n인터뷰 {interview_num}회차 완료.")
        cont = input("다음 인터뷰를 진행하시겠습니까? (y/n): ").strip().lower()
        if cont != "y":
            break
        interview_num += 1

    print("\n=== 모든 인터뷰가 완료되었습니다 ===")


if __name__ == "__main__":
    main()
