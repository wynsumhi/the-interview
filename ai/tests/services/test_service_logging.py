from pathlib import Path
from unittest.mock import patch

from app.services.interview_state_service import analyze_interview_state
from app.services.llm_service import create_message
from app.services.stt_service import speech_to_text
from app.services.tts_service import text_to_speech


def test_speech_to_text_logs_elapsed_time_and_text() -> None:
    with (
        patch("app.services.stt_service.transcribe_audio_file", return_value="candidate answer"),
        patch("app.services.stt_service.time.perf_counter", side_effect=[10.0, 10.25]),
        patch("app.services.stt_service.logger.info") as logger_mock,
    ):
        result = speech_to_text("sample.webm")

    assert result == "candidate answer"
    logger_mock.assert_called_once_with(
        "[STT] completed in %.3fs path=%s text=%s",
        0.25,
        "sample.webm",
        "candidate answer",
    )


def test_create_message_logs_elapsed_time_and_text() -> None:
    with (
        patch("app.services.llm_service.create_claude_message", return_value="follow-up question"),
        patch("app.services.llm_service.time.perf_counter", side_effect=[20.0, 20.4]),
        patch("app.services.llm_service.logger.info") as logger_mock,
    ):
        result = create_message(messages=[{"role": "user", "content": "hello"}], max_tokens=256)

    assert result == "follow-up question"
    logger_mock.assert_called_once_with(
        "[LLM] completed in %.3fs max_tokens=%s text=%s",
        0.3999999999999986,
        256,
        "follow-up question",
    )


def test_text_to_speech_logs_elapsed_time_and_size() -> None:
    with (
        patch("app.services.tts_service.synthesize_speech", return_value=b"wav-bytes"),
        patch("app.services.tts_service.time.perf_counter", side_effect=[30.0, 30.15]),
        patch("app.services.tts_service.logger.info") as logger_mock,
    ):
        result = text_to_speech("question text")

    assert result == b"wav-bytes"
    logger_mock.assert_called_once_with(
        "[TTS] completed in %.3fs chars=%s bytes=%s",
        0.14999999999999858,
        13,
        9,
    )


def test_analyze_interview_state_logs_summary() -> None:
    fake_result = (
        "[INTERVIEW_STATE] completed in %.3fs path=%s text_top=%s voice_top=%s "
        "fused_top=%s fused_scores=%s silence_ratio=%.3f pitch_mean_hz=%.3f pitch_std_hz=%.3f"
    )
    with (
        patch(
            "app.services.interview_state_service.extract_audio_features",
            return_value=type(
                "AudioFeaturesStub",
                (),
                {
                    "silence_ratio": 0.2,
                    "pitch_mean_hz": 120.0,
                    "pitch_std_hz": 10.0,
                },
            )(),
        ),
        patch(
            "app.services.interview_state_service.analyze_text_state",
            return_value=type("StateSignalStub", (), {"top_state": "calm"})(),
        ),
        patch(
            "app.services.interview_state_service.analyze_voice_state",
            return_value=type("StateSignalStub", (), {"top_state": "calm"})(),
        ),
        patch(
            "app.services.interview_state_service.fuse_signals",
            return_value=type(
                "StateSignalStub",
                (),
                {"top_state": "calm", "scores": {"calm": 0.8, "tense": 0.2}},
            )(),
        ),
        patch("app.services.interview_state_service.time.perf_counter", side_effect=[40.0, 40.1]),
        patch("app.services.interview_state_service.logger.info") as logger_mock,
    ):
        analyze_interview_state(str(Path("sample.wav")), "candidate answer")

    logger_mock.assert_called_once_with(
        fake_result,
        0.10000000000000142,
        "sample.wav",
        "calm",
        "calm",
        "calm",
        {"calm": 0.8, "tense": 0.2},
        0.2,
        120.0,
        10.0,
    )
