#!/usr/bin/env python3
"""꼬리 질문 생성 프롬프트 변형 테스트 스크립트.

실제 음성 파일로 STT → 감정 분석 → RAG → LLM 꼬리 질문 생성까지 전체 파이프라인을 실행한다.
TTS는 테스트에서 제외한다.

  variant 1: 기본 프롬프트만                (use_emotion=False, use_rag=False)
  variant 2: 기본 + 감정 상태              (use_emotion=True,  use_rag=False)
  variant 3: 기본 + RAG                   (use_emotion=False, use_rag=True)
  variant 4: 기본 + 감정 상태 + RAG        (use_emotion=True,  use_rag=True)

실행 결과는 tests/result/ 디렉터리에 MD 파일로 자동 저장된다.

사용 예시:
    # 기본 (tests/data/test-1.wav, 전체 variant)
    python tests/pipeline/run_prompt_variant_test.py

    # 음성 파일 직접 지정
    python tests/pipeline/run_prompt_variant_test.py --audio tests/data/test-2.wav

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
    run_interview_pipeline,
)
from app.prompts.interview_prompts import (  # noqa: E402
    _EMOTION_GUIDANCE,
    _INTERVIEW_BASE_PROMPT,
    _RAG_GUIDANCE,
    INTERVIEW_SYSTEM_PROMPT,
)
from app.services import interview_state_service, rag_service  # noqa: E402
from app.services.rag_service import upsert_interview_history_rag  # noqa: E402

# variant별 시스템 프롬프트 — 테스트 비교용으로만 사용
_VARIANT_PROMPTS: dict[int, str] = {
    1: _INTERVIEW_BASE_PROMPT,
    2: "\n\n".join([_INTERVIEW_BASE_PROMPT, _EMOTION_GUIDANCE]),
    3: "\n\n".join([_INTERVIEW_BASE_PROMPT, _RAG_GUIDANCE]),
    4: INTERVIEW_SYSTEM_PROMPT,
}

DEFAULT_AUDIO_PATH = str(Path(__file__).resolve().parents[1] / "data" / "test-1.wav")

# "test-user"의 과거 인터뷰 데이터 — 주말 활동, 일상 루틴, 취미 등 현재 테스트 답변과 연관된 주제
_RAG_TEST_TURNS = [
    {
        "question_id": "q-001",
        "answer_id": "a-001",
        "question": "운동 루틴을 꽤 꾸준히 이어가고 계신 것 같아요. 어떤 식으로 하고 계세요?",
        "answer": "주 3~4회 정도 헬스장에서 운동하고 있고, 거의 루틴처럼 자리 잡았어요.",
        "turn_index": 1,
        "metadata": {"topic": "routine"},
    },
    {
        "question_id": "q-002",
        "answer_id": "a-002",
        "question": "요즘 자주 읽는 글이나 콘텐츠가 있나요?",
        "answer": "최근엔 에세이를 많이 읽어요. 누군가의 일상 같은 글이 특히 좋더라고요.",
        "turn_index": 2,
        "metadata": {"topic": "lifestyle"},
    },
    {
        "question_id": "q-003",
        "answer_id": "a-003",
        "question": "책을 고를 때 어떤 글에 끌리세요?",
        "answer": "사건이 큰 이야기보다 평범한 하루를 섬세하게 적은 글에 더 마음이 가요.",
        "turn_index": 3,
        "metadata": {"topic": "hobby"},
    },
]

VARIANT_LABELS = {
    1: "기본 프롬프트만",
    2: "기본 + 감정 상태",
    3: "기본 + RAG",
    4: "기본 + 감정 상태 + RAG",
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
    audio_file_path: str,
    variant: int,
    user_id: str | None,
    previous_question: str | None,
    max_tokens: int,
) -> dict:
    timing: dict[str, float] = {}

    original_analyze = interview_state_service.analyze_interview_state
    original_retrieve = rag_service.retrieve_similar_interview_history

    start = time.perf_counter()
    with (
        patch("app.pipeline.interview_pipeline.text_to_speech", return_value=b""),
        patch("app.pipeline.interview_pipeline.INTERVIEW_SYSTEM_PROMPT", _VARIANT_PROMPTS[variant]),
        patch(
            "app.pipeline.interview_pipeline.analyze_interview_state",
            side_effect=_make_timed(original_analyze, timing, "emotion"),
        ),
        patch(
            "app.pipeline.interview_pipeline.retrieve_similar_interview_history",
            side_effect=_make_timed(original_retrieve, timing, "rag"),
        ),
    ):
        result = run_interview_pipeline(
            audio_file_path=audio_file_path,
            is_first_finished=True,
            user_id=user_id,
            previous_question=previous_question,
            max_tokens=max_tokens,
        )
    elapsed = round(time.perf_counter() - start, 2)

    af = result.interview_state_db.audio_features
    return {
        "variant": variant,
        "label": VARIANT_LABELS[variant],
        "stt_text": result.stt_text,
        "question_text": result.llm_text,
        "top_state": result.interview_state_db.top_state,
        "fused_scores": result.interview_state_db.fused_scores,
        "audio_features": {
            "duration_sec": af.duration_sec,
            "rms_mean": af.rms_mean,
            "pitch_mean_hz": af.pitch_mean_hz,
            "pitch_std_hz": af.pitch_std_hz,
            "silence_ratio": af.silence_ratio,
            "voiced_ratio": af.voiced_ratio,
            "speech_rate_proxy": af.speech_rate_proxy,
        },
        "emotion_prompt": result.interview_state_prompt.formatted_prompt,
        "elapsed_sec": elapsed,
        "emotion_elapsed_sec": timing.get("emotion"),
        "rag_elapsed_sec": timing.get("rag"),
    }


def save_as_markdown(
    audio_file_path: str,
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
        f"- **음성 파일**: {audio_file_path}",
        f"- **직전 질문**: {previous_question or '(없음)'}",
        "",
        "## 소요 시간 요약",
        "",
        "| Variant | 전체 | 감정 분석 | RAG 검색 |",
        "|---------|------|-----------|----------|",
    ]
    for entry in results:
        label = f"V{entry['variant']}: {entry['label']}"
        emotion = f"{entry['emotion_elapsed_sec']}s" if entry.get("emotion_elapsed_sec") else "-"
        rag = f"{entry['rag_elapsed_sec']}s" if entry.get("rag_elapsed_sec") else "-"
        lines.append(f"| {label} | {entry['elapsed_sec']}s | {emotion} | {rag} |")
    lines += [
        f"| **전체 합계** | **{total_elapsed:.2f}s** | | |",
        "",
        "---",
        "",
    ]

    for entry in results:
        lines.append(f"## Variant {entry['variant']}: {entry['label']}")
        lines.append("")
        lines.append(f"- **STT 결과**: {entry['stt_text']}")
        lines.append("")
        lines.append("### 감정 분석 결과")
        lines.append("")
        scores = ", ".join(f"{k}={v:.2f}" for k, v in entry["fused_scores"].items())
        lines.append(f"- **top_state**: {entry['top_state']}")
        lines.append(f"- **fused_scores**: {scores}")
        af = entry["audio_features"]
        lines.append(
            f"- **audio_features**: duration={af['duration_sec']}s"
            f", rms_mean={af['rms_mean']:.4f}"
            f", pitch={af['pitch_mean_hz']:.1f}±{af['pitch_std_hz']:.1f}Hz"
            f", silence={af['silence_ratio']:.2f}"
            f", voiced={af['voiced_ratio']:.2f}"
            f", speech_rate={af['speech_rate_proxy']:.3f}"
        )
        lines.append("- **LLM 전달 감정 컨텍스트**:")
        lines.append("")
        lines.append("```")
        lines.append(entry["emotion_prompt"])
        lines.append("```")
        lines.append("")
        emotion = f"{entry['emotion_elapsed_sec']}s" if entry.get("emotion_elapsed_sec") else "-"
        rag = f"{entry['rag_elapsed_sec']}s" if entry.get("rag_elapsed_sec") else "-"
        lines.append(
            f"- 소요 시간: 전체 {entry['elapsed_sec']}s  |  감정 분석 {emotion}  |  RAG {rag}"
        )
        lines.append("")
        lines.append("### 꼬리 질문")
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
    print(f"  STT 결과    : {entry['stt_text']}")
    print(f"  top_state   : {entry['top_state']}")
    scores = ", ".join(f"{k}={v:.2f}" for k, v in entry["fused_scores"].items())
    print(f"  fused_scores: {scores}")
    af = entry["audio_features"]
    print(
        f"  audio       : duration={af['duration_sec']}s"
        f", rms={af['rms_mean']:.4f}"
        f", pitch={af['pitch_mean_hz']:.1f}±{af['pitch_std_hz']:.1f}Hz"
        f", silence={af['silence_ratio']:.2f}"
        f", voiced={af['voiced_ratio']:.2f}"
        f", speech_rate={af['speech_rate_proxy']:.3f}"
    )
    print("  감정 컨텍스트:")
    for line in entry["emotion_prompt"].splitlines():
        print(f"    {line}")
    print(f"  꼬리 질문   : {entry['question_text']}")
    emotion = f"{entry['emotion_elapsed_sec']}s" if entry.get("emotion_elapsed_sec") else "-"
    rag = f"{entry['rag_elapsed_sec']}s" if entry.get("rag_elapsed_sec") else "-"
    print(f"  소요 시간   : 전체 {entry['elapsed_sec']}s  |  감정 분석 {emotion}  |  RAG {rag}")


def print_timing_summary(results: list[dict]) -> None:
    total = sum(e["elapsed_sec"] for e in results)
    print("\n" + "=" * 60)
    print("  소요 시간 요약")
    print("=" * 60)
    print(f"  {'Variant':<30} {'전체':>6}  {'감정 분석':>8}  {'RAG':>6}")
    print(f"  {'─' * 54}")
    for entry in results:
        label = f"V{entry['variant']}: {entry['label']}"
        emotion = f"{entry['emotion_elapsed_sec']}s" if entry.get("emotion_elapsed_sec") else "-"
        rag = f"{entry['rag_elapsed_sec']}s" if entry.get("rag_elapsed_sec") else "-"
        print(f"  {label:<30} {entry['elapsed_sec']:>5}s  {emotion:>8}  {rag:>6}")
    print(f"  {'─' * 54}")
    print(f"  {'전체 합계':<30} {total:>5.2f}s")
    print("=" * 60)


def main() -> None:
    parser = argparse.ArgumentParser(description="프롬프트 변형별 꼬리 질문 생성 테스트")
    parser.add_argument(
        "--audio",
        default=DEFAULT_AUDIO_PATH,
        help="STT에 사용할 음성 파일 경로 (기본값: tests/data/test-1.wav)",
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

    audio_path = Path(args.audio)
    if not audio_path.exists():
        print(f"  [오류] 음성 파일을 찾을 수 없습니다: {audio_path}")
        sys.exit(1)

    print("=" * 60)
    print("  프롬프트 변형별 꼬리 질문 생성 테스트")
    print("=" * 60)
    print(f"\n  음성 파일  : {args.audio}")
    print(f"  직전 질문  : {args.previous_question}")
    print(f"  실행 variants: {args.variants}\n")

    if any(v in (3, 4) for v in args.variants):
        print("  RAG 테스트 데이터 준비 중...")
        setup_rag_test_data(args.user_id)
        print()

    # ── 각 Variant 실행 ───────────────────────────────────────────
    results = []
    for variant in sorted(set(args.variants)):
        print(f"  [Variant {variant}] {VARIANT_LABELS[variant]} 실행 중...")
        entry = run_variant(
            audio_file_path=args.audio,
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
        audio_file_path=args.audio,
        previous_question=args.previous_question,
        results=results,
    )
    print(f"\n  결과 저장 완료: {output_path}")


if __name__ == "__main__":
    main()
