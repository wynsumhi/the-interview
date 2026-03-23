import os
import re
import sys
from pathlib import Path

sys.path.append(os.path.join(os.path.dirname(__file__), "../.."))

from app.services.interview_service import refine_answer_for_magazine

"""
매거진 형태 정제 프롬프트 테스트

tests/data/interview_2_raw.md 의 구어체 답변을
MAGAZINE_GENERATION_SYSTEM_PROMPT 로 정제한 결과를 before/after 형태로 출력하고
tests/result/{label}_refined.md 파일로 저장합니다.

실행 방법:
    cd ai
    python tests/services/test_magazine_refinement.py
"""

DATA_DIR = Path(__file__).parent.parent / "data"
RESULT_DIR = Path(__file__).parent.parent / "result"
RAW_FILES = [
    ("interview_2_raw", DATA_DIR / "interview_2_raw.md")
]


def parse_qa_pairs(md_text: str) -> list[dict[str, str]]:
    """마크다운 형식의 인터뷰 파일에서 Q&A 쌍을 순서대로 추출한다."""
    questions = re.findall(r"\*\*Q\d+\.\*\*\s*(.+)", md_text)
    answers = re.findall(r"\*\*A\.\*\*\s*(.+)", md_text)
    return [{"question": q.strip(), "answer": a.strip()} for q, a in zip(questions, answers)]


def build_result_md(label: str, qa_pairs: list[dict[str, str]], refined_answers: list[str]) -> str:
    """정제 결과를 마크다운 형식으로 구성한다."""
    lines = [f"# {label} — 매거진 정제 결과\n"]
    for i, (pair, refined) in enumerate(zip(qa_pairs, refined_answers), start=1):
        lines.append(f"## Q{i}. {pair['question']}\n")
        lines.append(f"**BEFORE**\n{pair['answer']}\n")
        lines.append(f"**AFTER**\n{refined}\n")
        lines.append("---\n")
    return "\n".join(lines)


if __name__ == "__main__":
    RESULT_DIR.mkdir(parents=True, exist_ok=True)

    for label, path in RAW_FILES:
        md_text = path.read_text(encoding="utf-8")
        qa_pairs = parse_qa_pairs(md_text)
        refined_answers = []

        print(f"\n{'=' * 70}")
        print(f"  {label}  ({len(qa_pairs)}개 Q&A)")
        print(f"{'=' * 70}")

        for i, pair in enumerate(qa_pairs, start=1):
            refined = refine_answer_for_magazine(pair["answer"])["refined_answer"]
            refined_answers.append(refined)

            print(f"\n[Q{i}]  {pair['question']}")
            print(f"  BEFORE │ {pair['answer']}")
            print(f"  AFTER  │ {refined}")

        result_path = RESULT_DIR / f"{label}_refined.md"
        result_md = build_result_md(label, qa_pairs, refined_answers)
        result_path.write_text(result_md, encoding="utf-8")
        print(f"\n  → 저장 완료: {result_path}")
