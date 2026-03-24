from __future__ import annotations

import argparse
import json
import sys
import wave
from dataclasses import asdict
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Any
from unittest.mock import patch

from app.pipeline.interview_pipeline import (
    run_interview_pipeline,
    run_interview_pipeline_from_stt_text,
)
from app.services.interview_state_service import (
    AudioFeatures,
    InterviewStateDbPayload,
    InterviewStatePromptPayload,
)

DEFAULT_STT_TEXT = (
    "My phone number is 010-1234-5678, my email is test.user@example.com, "
    "and my resident number is 900101-1234567. "
    "Please send the interview draft to account 123-45-678901."
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run a local verification flow to inspect how interview-answer STT is masked."
    )
    parser.add_argument(
        "--audio-file",
        type=Path,
        help="Optional audio file path. If omitted, a temporary silent wav file is created.",
    )
    parser.add_argument(
        "--stt-text",
        default=DEFAULT_STT_TEXT,
        help="STT text to feed into the pipeline. Used by default for MR checks.",
    )
    parser.add_argument(
        "--run-stt",
        action="store_true",
        help="Run the STT step from the audio file instead of using --stt-text.",
    )
    parser.add_argument(
        "--user-id",
        default="pii-check-user",
        help="Optional user id used for the regular interview branch.",
    )
    parser.add_argument(
        "--previous-question",
        default="Tell me about a moment that changed how you work.",
        help="Previous question used for the regular interview branch.",
    )
    return parser.parse_args()


def create_silent_wav_file() -> str:
    with NamedTemporaryFile(delete=False, suffix=".wav") as temp_file:
        temp_path = Path(temp_file.name)

    with wave.open(str(temp_path), "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(16000)
        wav_file.writeframes(b"\x00\x00" * 16000)

    return str(temp_path)


def build_fake_interview_state() -> tuple[InterviewStateDbPayload, InterviewStatePromptPayload]:
    fused_scores = {
        "calm": 0.82,
        "tense": 0.12,
        "anxious": 0.03,
        "low": 0.01,
        "engaged": 0.02,
    }
    audio_features = AudioFeatures(
        duration_sec=1.0,
        rms_mean=0.02,
        rms_std=0.01,
        zcr_mean=0.1,
        silence_ratio=0.2,
        voiced_ratio=0.8,
        speech_rate_proxy=0.8,
        pitch_mean_hz=120.0,
        pitch_std_hz=10.0,
    )
    interview_state_db = InterviewStateDbPayload(
        top_state="calm",
        fused_scores=fused_scores,
        audio_features=audio_features,
    )
    interview_state_prompt = InterviewStatePromptPayload(
        top_state="calm",
        fused_scores=fused_scores,
        formatted_prompt=(
            "[Interview State]\n"
            "- top_state: calm\n"
            "- fused_scores: calm=0.82, tense=0.12, anxious=0.03, low=0.01, engaged=0.02"
        ),
    )
    return interview_state_db, interview_state_prompt


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    args = parse_args()

    created_temp_audio = args.audio_file is None
    audio_file_path = str(args.audio_file) if args.audio_file else create_silent_wav_file()
    interview_state_db, interview_state_prompt = build_fake_interview_state()
    captured_messages: dict[str, Any] = {}

    def fake_create_message(*, messages: list[dict[str, str]], system: str, max_tokens: int) -> str:
        captured_messages["messages"] = messages
        captured_messages["system"] = system
        captured_messages["max_tokens"] = max_tokens
        return "What detail from that experience still feels vivid to you?"

    try:
        with (
            patch(
                "app.pipeline.interview_pipeline.analyze_interview_state",
                return_value=object(),
            ),
            patch(
                "app.pipeline.interview_pipeline.build_interview_state_db_payload",
                return_value=interview_state_db,
            ),
            patch(
                "app.pipeline.interview_pipeline.build_interview_state_prompt_payload",
                return_value=interview_state_prompt,
            ),
            patch(
                "app.pipeline.interview_pipeline.retrieve_similar_interview_history",
                return_value=[],
            ),
            patch(
                "app.pipeline.interview_pipeline.format_retrieved_turns_for_prompt",
                return_value="",
            ),
            patch(
                "app.pipeline.interview_pipeline.create_message",
                side_effect=fake_create_message,
            ),
            patch(
                "app.pipeline.interview_pipeline.text_to_speech",
                return_value=b"fake-tts-bytes",
            ),
            patch(
                "app.pipeline.interview_pipeline.speech_to_text",
                return_value=args.stt_text,
            ),
        ):
            if args.run_stt:
                result = run_interview_pipeline(
                    audio_file_path=audio_file_path,
                    is_first_finished=True,
                    user_id=args.user_id,
                    previous_question=args.previous_question,
                )
            else:
                result = run_interview_pipeline_from_stt_text(
                    audio_file_path=audio_file_path,
                    stt_text=args.stt_text,
                    is_first_finished=True,
                    user_id=args.user_id,
                    previous_question=args.previous_question,
                )
    finally:
        if created_temp_audio:
            Path(audio_file_path).unlink(missing_ok=True)

    response = result.to_response_dict()
    llm_input = (captured_messages.get("messages") or [{"content": ""}])[0]["content"]

    print("=== Interview PII Masking Check ===")
    print(f"audio_file_path: {audio_file_path}")
    mode = "run_interview_pipeline" if args.run_stt else "run_interview_pipeline_from_stt_text"
    print(f"mode: {mode}")
    print()
    print("[Original STT]")
    print(response["stt_text"])
    print()
    print("[Masked STT]")
    print(response["masked_stt_text"])
    print()
    print("[Masking Time]")
    print(f"{response['pii_masking_duration_seconds']} seconds")
    print()
    print("[LLM Input]")
    print(llm_input)
    print()
    print("[Generated Question]")
    print(response["question_text"])
    print()
    print("[Interview State DB]")
    print(json.dumps(asdict(interview_state_db), ensure_ascii=False, indent=2))
    print()
    print("[Interview State Prompt]")
    print(json.dumps(asdict(interview_state_prompt), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
