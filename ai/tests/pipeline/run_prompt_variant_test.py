#!/usr/bin/env python3
"""꼬리 질문 생성 프롬프트 변형 테스트 스크립트.

텍스트를 직접 입력받아 4가지 prompt_variant에 대해 꼬리 질문 생성 결과를 비교한다.
오디오 파일은 사용하지 않으며, 감정 상태는 테스트용 고정값을 사용한다.

  variant 1: 기본 프롬프트만                (use_emotion=False, use_rag=False)
  variant 2: 기본 + 감정 상태              (use_emotion=True,  use_rag=False)
  variant 3: 기본 + RAG                   (use_emotion=False, use_rag=True)
  variant 4: 기본 + 감정 상태 + RAG        (use_emotion=True,  use_rag=True)

실행 결과는 tests/result/ 디렉터리에 MD 파일로 자동 저장된다.

사용 예시:
    # 기본 (텍스트 기본값, 전체 variant)
    python tests/pipeline/run_prompt_variant_test.py

    # 텍스트 직접 지정
    python tests/pipeline/run_prompt_variant_test.py --stt-text "오늘 날씨가 좋아서 산책했어요."

    # variant 1, 2만 비교
    python tests/pipeline/run_prompt_variant_test.py --variants 1 2
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from app.pipeline.interview_pipeline import (  # noqa: E402
    run_interview_pipeline_from_stt_text,
)
from app.prompts.interview_prompts import build_interview_system_prompt  # noqa: E402
from app.services import rag_service  # noqa: E402
from app.services.interview_state_service import (  # noqa: E402
    AudioFeatures,
    InterviewStateResult,
    StateSignal,
)
from app.services.rag_service import upsert_interview_history_rag  # noqa: E402

DEFAULT_STT_TEXT = "최근에는 주말마다 운동하고 책 읽으면서 시간을 보내고 있어요."

# 감정 분석 mock 고정값 — "주말마다 운동하고 책 읽는" 답변에 어울리는 calm 상태
_MOCK_INTERVIEW_STATE = InterviewStateResult(
    transcript=DEFAULT_STT_TEXT,
    audio_features=AudioFeatures(
        duration_sec=4.2,
        rms_mean=0.045,
        rms_std=0.012,
        zcr_mean=0.08,
        silence_ratio=0.28,
        voiced_ratio=0.72,
        speech_rate_proxy=0.15,
        pitch_mean_hz=185.0,
        pitch_std_hz=22.0,
    ),
    text_signal=StateSignal(
        scores={"calm": 0.55, "engaged": 0.25, "tense": 0.1, "anxious": 0.05, "low": 0.05},
        top_state="calm",
        top_score=0.55,
        reason="텍스트 분석: calm",
    ),
    voice_signal=StateSignal(
        scores={"calm": 0.6, "engaged": 0.2, "tense": 0.1, "anxious": 0.05, "low": 0.05},
        top_state="calm",
        top_score=0.6,
        reason="음성 분석: calm",
    ),
    fused_signal=StateSignal(
        scores={"calm": 0.57, "engaged": 0.23, "tense": 0.1, "anxious": 0.05, "low": 0.05},
        top_state="calm",
        top_score=0.57,
        reason="융합: calm",
    ),
)

# "test-user"의 과거 인터뷰 데이터 — 주말 활동, 일상 루틴, 취미 등 현재 테스트 답변과 연관된 주제
_RAG_TEST_TURNS = [
    {
        "question_id": "q-001",
        "answer_id": "a-001",
        "question": "하루 중 가장 에너지를 많이 쏟는 시간대는 언제인가요?",
        "answer": "오전에 운동을 마치고 나면 머리가 맑아져서, 그때 가장 집중이 잘 돼요."
        "주로 오전 시간을 생산적인 일에 씁니다.",
        "turn_index": 1,
        "metadata": {"topic": "routine", "mood": "energetic"},
    },
    {
        "question_id": "q-002",
        "answer_id": "a-002",
        "question": "혼자 있는 시간을 즐기는 편인가요, 아니면 사람들과 함께 있는 게 더 편한가요?",
        "answer": "혼자 책 읽거나 산책하는 시간이 저한테는 충전의 시간이에요."
        "사람들과 있는 것도 좋지만, 혼자만의 조용한 시간이 꼭 필요해요.",
        "turn_index": 2,
        "metadata": {"topic": "lifestyle", "mood": "calm"},
    },
    {
        "question_id": "q-003",
        "answer_id": "a-003",
        "question": "요즘 빠져 있는 취미나 관심사가 있나요?",
        "answer": "최근 독서에 다시 빠졌어요. 특히 에세이 장르를 좋아하는데, 마음이 따뜻해져요.",
        "turn_index": 3,
        "metadata": {"topic": "hobby", "mood": "warm"},
    },
]

VARIANT_LABELS = {
    1: "기본 프롬프트만",
    2: "기본 + 감정 상태",
    3: "기본 + RAG",
    4: "기본 + 감정 상태 + RAG",
}

VARIANT_FLAGS: dict[int, tuple[bool, bool]] = {
    1: (False, False),
    2: (True, False),
    3: (False, True),
    4: (True, True),
}


def setup_rag_test_data(user_id: str) -> None:
    """RAG 테스트용 과거 인터뷰 데이터를 Qdrant에 삽입한다."""

    upsert_interview_history_rag(
        {
            "user_id": user_id,
            "interview_id": "test-interview-001",
            "turns": _RAG_TEST_TURNS,
            "metadata": {"source": "variant_test"},
        }
    )
    print(f"  RAG 테스트 데이터 삽입 완료 ({len(_RAG_TEST_TURNS)}건, user_id={user_id})")


def _make_timed(fn, timing: dict, key: str):
    """함수를 감싸 호출 시간을 timing[key]에 기록하는 wrapper를 반환한다."""

    def wrapper(*args, **kwargs):
        t = time.perf_counter()
        result = fn(*args, **kwargs)
        timing[key] = round(time.perf_counter() - t, 2)
        return result

    return wrapper


def run_variant(
    stt_text: str,
    variant: int,
    user_id: str | None,
    previous_question: str | None,
    max_tokens: int,
) -> dict:
    use_emotion, use_rag = VARIANT_FLAGS[variant]
    system_prompt = build_interview_system_prompt(use_emotion=use_emotion, use_rag=use_rag)
    timing: dict[str, float] = {}

    original_retrieve = rag_service.retrieve_similar_interview_history

    start = time.perf_counter()
    with (
        patch("app.pipeline.interview_pipeline.text_to_speech", return_value=b""),
        patch("app.pipeline.interview_pipeline.INTERVIEW_SYSTEM_PROMPT", system_prompt),
        patch(
            "app.pipeline.interview_pipeline.analyze_interview_state",
            return_value=_MOCK_INTERVIEW_STATE,
        ),
        patch(
            "app.pipeline.interview_pipeline.retrieve_similar_interview_history",
            side_effect=_make_timed(original_retrieve, timing, "rag"),
        ),
    ):
        result = run_interview_pipeline_from_stt_text(
            audio_file_path="dummy.wav",
            stt_text=stt_text,
            is_first_finished=True,
            user_id=user_id,
            previous_question=previous_question,
            max_tokens=max_tokens,
            use_emotion=use_emotion,
            use_rag=use_rag,
        )
    elapsed = round(time.perf_counter() - start, 2)

    return {
        "variant": variant,
        "label": VARIANT_LABELS[variant],
        "question_text": result.llm_text,
        "top_state": result.interview_state_db.top_state,
        "fused_scores": result.interview_state_db.fused_scores,
        "elapsed_sec": elapsed,
        "rag_elapsed_sec": timing.get("rag"),
    }


def save_as_markdown(
    stt_text: str,
    previous_question: str | None,
    results: list[dict],
) -> Path:
    result_dir = Path(__file__).resolve().parents[1] / "result"
    result_dir.mkdir(exist_ok=True)

    timestamp = time.strftime("%Y%m%d_%H%M%S")
    output_path = result_dir / f"variant_test_{timestamp}.md"

    total_elapsed = sum(e["elapsed_sec"] for e in results)

    lines = [
        "# 프롬프트 변형 테스트 결과",
        "",
        f"- **날짜**: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- **답변 텍스트**: {stt_text}",
        f"- **직전 질문**: {previous_question or '(없음)'}",
        "",
        "## 소요 시간 요약",
        "",
        "| Variant | 전체 | RAG 검색 |",
        "|---------|------|----------|",
    ]
    for entry in results:
        label = f"V{entry['variant']}: {entry['label']}"
        rag = f"{entry['rag_elapsed_sec']}s" if entry.get("rag_elapsed_sec") else "-"
        lines.append(f"| {label} | {entry['elapsed_sec']}s | {rag} |")
    lines += [
        f"| **전체 합계** | **{total_elapsed:.2f}s** | |",
        "",
        "---",
        "",
    ]

    for entry in results:
        lines.append(f"## Variant {entry['variant']}: {entry['label']}")
        lines.append("")
        scores = ", ".join(f"{k}={v:.2f}" for k, v in entry["fused_scores"].items())
        lines.append(f"- **top_state**: {entry['top_state']}")
        lines.append(f"- **fused_scores**: {scores}")
        rag = f"{entry['rag_elapsed_sec']}s" if entry.get("rag_elapsed_sec") else "-"
        lines.append(f"- **소요 시간**: 전체 {entry['elapsed_sec']}s  |  RAG 검색 {rag}")
        lines.append("")
        lines.append("**꼬리 질문**")
        lines.append("")
        lines.append(f"> {entry['question_text']}")
        lines.append("")
        lines.append("---")
        lines.append("")

    output_path.write_text("\n".join(lines), encoding="utf-8")
    return output_path


def print_result(entry: dict) -> None:
    sep = "─" * 60
    print(sep)
    print(f"  Variant {entry['variant']}: {entry['label']}")
    print(f"  top_state   : {entry['top_state']}")
    scores = ", ".join(f"{k}={v:.2f}" for k, v in entry["fused_scores"].items())
    print(f"  fused_scores: {scores}")
    print(f"  꼬리 질문   : {entry['question_text']}")
    rag = f"{entry['rag_elapsed_sec']}s" if entry.get("rag_elapsed_sec") else "-"
    print(f"  소요 시간   : 전체 {entry['elapsed_sec']}s  |  RAG {rag}")


def print_timing_summary(results: list[dict]) -> None:
    total = sum(e["elapsed_sec"] for e in results)
    print("\n" + "=" * 60)
    print("  소요 시간 요약")
    print("=" * 60)
    print(f"  {'Variant':<30} {'전체':>6}  {'RAG':>6}")
    print(f"  {'─' * 46}")
    for entry in results:
        label = f"V{entry['variant']}: {entry['label']}"
        rag = f"{entry['rag_elapsed_sec']}s" if entry.get("rag_elapsed_sec") else "-"
        print(f"  {label:<30} {entry['elapsed_sec']:>5}s  {rag:>6}")
    print(f"  {'─' * 46}")
    print(f"  {'전체 합계':<30} {total:>5.2f}s")
    print("=" * 60)


def main() -> None:
    parser = argparse.ArgumentParser(description="프롬프트 변형별 꼬리 질문 생성 테스트")
    parser.add_argument(
        "--stt-text",
        default=DEFAULT_STT_TEXT,
        help=f"꼬리 질문 생성에 사용할 답변 텍스트 (기본값: {DEFAULT_STT_TEXT!r})",
    )
    parser.add_argument(
        "--user-id",
        default="test-user",
        help="RAG 검색에 사용할 사용자 ID (기본값: test-user)",
    )
    parser.add_argument(
        "--previous-question",
        default="주말에는 주로 무엇을 하며 시간을 보내세요?",
        help="직전 인터뷰 질문 텍스트",
    )
    parser.add_argument(
        "--variants",
        nargs="+",
        type=int,
        choices=[1, 2, 3, 4],
        default=[1, 2, 3, 4],
        help="실행할 variant 번호 (기본값: 1 2 3 4)",
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=256,
        help="LLM 최대 토큰 수 (기본값: 256)",
    )
    args = parser.parse_args()

    print("=" * 60)
    print("  프롬프트 변형별 꼬리 질문 생성 테스트")
    print("=" * 60)
    print(f"\n  답변 텍스트: {args.stt_text}")
    print(f"  직전 질문  : {args.previous_question}")
    print(f"  실행 variants: {args.variants}\n")

    if any(VARIANT_FLAGS[v][1] for v in args.variants):
        print("  RAG 테스트 데이터 준비 중...")
        setup_rag_test_data(args.user_id)
        print()

    # ── 각 Variant 실행 ───────────────────────────────────────────
    results = []
    for variant in sorted(set(args.variants)):
        print(f"  [Variant {variant}] {VARIANT_LABELS[variant]} 실행 중...")
        entry = run_variant(
            stt_text=args.stt_text,
            variant=variant,
            user_id=args.user_id,
            previous_question=args.previous_question,
            max_tokens=args.max_tokens,
        )
        results.append(entry)

    # ── 결과 출력 ─────────────────────────────────────────────────
    print("\n" + "=" * 60)
    print("  결과 요약")
    print("=" * 60)
    for entry in results:
        print_result(entry)
    print("─" * 60)
    print_timing_summary(results)

    # ── MD 저장 (자동) ────────────────────────────────────────────
    output_path = save_as_markdown(
        stt_text=args.stt_text,
        previous_question=args.previous_question,
        results=results,
    )
    print(f"\n  결과 저장 완료: {output_path}")


if __name__ == "__main__":
    main()
