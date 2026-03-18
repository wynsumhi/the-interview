from unittest.mock import patch

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
        "STT completed in %.3fs. path=%s text=%s",
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
        "LLM completed in %.3fs. max_tokens=%s text=%s",
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
        "TTS completed in %.3fs. chars=%s bytes=%s",
        0.14999999999999858,
        13,
        9,
    )
