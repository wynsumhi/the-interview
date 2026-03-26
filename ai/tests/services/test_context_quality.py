import os
import re
import sys
from pathlib import Path

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.services.interview_service import (
    generate_magazine_title,
    generate_next_opening_question,
)

"""
STT 원문 vs 정제 텍스트 기반의 다음 질문 / 매거진 타이틀 퀄리티 비교 테스트

실행 방법:
    cd ai
    python tests/services/test_context_quality.py
"""

DATA_DIR = Path(__file__).parent.parent / "data"
RESULT_DIR = Path(__file__).parent.parent / "result"

RAW_FILE = DATA_DIR / "interview_raw.md"
REFINED_FILE = RESULT_DIR / "interview_raw_refined.md"


def parse_raw_qa(text: str) -> list[dict[str, str]]:
    """평문 Q&A 형식 (Q1. / A1.) 파싱"""
    questions = re.findall(r"Q\d+\.\s*(.+)", text)
    answers = re.findall(r"A\d+\.\s*(.+)", text)
    return [
        {"question": q.strip(), "answer": a.strip()}
        for q, a in zip(questions, answers, strict=False)
    ]


def parse_refined_qa(text: str) -> list[dict[str, str]]:
    """정제 결과 마크다운 형식 (## Q{n}. 질문 / 본문 답변) 파싱"""
    blocks = re.split(r"\n---\n", text)
    qa_pairs = []
    for block in blocks:
        q_match = re.search(r"## Q\d+\.\s*(.+)", block)
        # 헤더와 --- 사이의 본문을 답변으로 사용
        body = re.sub(r"## Q\d+\..+\n", "", block).strip()
        if q_match and body:
            qa_pairs.append({"question": q_match.group(1).strip(), "answer": body})
    return qa_pairs


def build_context(qa_pairs: list[dict[str, str]]) -> str:
    lines = []
    for i, pair in enumerate(qa_pairs, start=1):
        lines.append(f"Q{i}. {pair['question']}")
        lines.append(f"A{i}. {pair['answer']}")
        lines.append("")
    return "\n".join(lines).strip()


def build_result_md(
    raw_title: str,
    refined_title: str,
    raw_question: str,
    refined_question: str,
) -> str:
    lines = [
        "# STT 원문 vs 정제 텍스트 — 매거진 타이틀 / 다음 질문 비교\n",
        "## 매거진 타이틀\n",
        "| | 결과 |",
        "| --- | --- |",
        f"| **STT 원문** | {raw_title} |",
        f"| **정제 텍스트** | {refined_title} |",
        "\n---\n",
        "## 다음 회차 최초 질문\n",
        "| | 결과 |",
        "| --- | --- |",
        f"| **STT 원문** | {raw_question} |",
        f"| **정제 텍스트** | {refined_question} |",
    ]
    return "\n".join(lines)


if __name__ == "__main__":
    RESULT_DIR.mkdir(parents=True, exist_ok=True)

    raw_pairs = parse_raw_qa(RAW_FILE.read_text(encoding="utf-8"))
    refined_pairs = parse_refined_qa(REFINED_FILE.read_text(encoding="utf-8"))

    raw_context = build_context(raw_pairs)
    refined_context = build_context(refined_pairs)

    print(f"\n{'=' * 70}")
    print(f"  Q&A 수: 원문 {len(raw_pairs)}개 / 정제 {len(refined_pairs)}개")
    print(f"{'=' * 70}")

    print("\n[타이틀 생성 중...]")
    raw_title = generate_magazine_title(raw_context)["title"]
    refined_title = generate_magazine_title(refined_context)["title"]
    print(f"  STT 원문  → {raw_title}")
    print(f"  정제 텍스트 → {refined_title}")

    print("\n[다음 질문 생성 중...]")
    raw_question = generate_next_opening_question(raw_context)["db_question"]
    refined_question = generate_next_opening_question(refined_context)["db_question"]
    print(f"  STT 원문  → {raw_question}")
    print(f"  정제 텍스트 → {refined_question}")

    result_md = build_result_md(raw_title, refined_title, raw_question, refined_question)
    result_path = RESULT_DIR / "interview_raw_context_quality.md"
    result_path.write_text(result_md, encoding="utf-8")
    print(f"\n  → 저장 완료: {result_path}")
