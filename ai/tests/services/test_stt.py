from io import BytesIO
from unittest.mock import patch

import numpy as np
import pytest
from app.services.stt_service import (
    NoSpeechAnalysis,
    _audiosegment_to_mono_float32,
    is_no_speech_audio_file,
    sanitize_stt_text,
    speech_to_text,
    speech_to_text_from_upload,
    validate_audio_upload,
)
from fastapi import UploadFile
from pydub import AudioSegment
from pydub.generators import Sine


def _analysis(
    *,
    peak_rms: float = 0.05,
    dynamic_threshold: float = 0.02,
    voiced_ms: int = 200,
    duration_ms: int = 3000,
    voiced_ratio: float = 0.06,
    is_no_speech: bool = False,
) -> NoSpeechAnalysis:
    return NoSpeechAnalysis(
        peak_rms=peak_rms,
        dynamic_threshold=dynamic_threshold,
        voiced_ms=voiced_ms,
        duration_ms=duration_ms,
        voiced_ratio=voiced_ratio,
        is_no_speech=is_no_speech,
    )


def test_validate_audio_upload_rejects_non_webm() -> None:
    upload = UploadFile(filename="answer.wav", file=BytesIO(b"wav-audio"))

    with pytest.raises(ValueError, match="Only \\.webm audio uploads are supported\\."):
        validate_audio_upload(upload)


def test_speech_to_text_from_upload_returns_transcription() -> None:
    upload = UploadFile(filename="answer.webm", file=BytesIO(b"webm-audio"))

    with patch(
        "app.services.stt_service.speech_to_text",
        return_value="transcribed text",
    ) as stt_mock:
        result = speech_to_text_from_upload(upload)

    assert result == {"stt_text": "transcribed text"}
    stt_mock.assert_called_once()


def test_sanitize_stt_text_filters_known_silence_hallucination_when_audio_is_noise_like() -> None:
    assert (
        sanitize_stt_text(
            "MBC 시청해주셔서 감사합니다. 구독과 좋아요 부탁드립니다.",
            analysis=_analysis(peak_rms=0.18, voiced_ms=290, voiced_ratio=0.097),
        )
        == ""
    )


def test_sanitize_stt_text_filters_next_video_phrase_when_audio_is_sparse() -> None:
    assert (
        sanitize_stt_text(
            "다음 영상에서 만나요",
            analysis=_analysis(
                peak_rms=0.18355, voiced_ms=380, duration_ms=5040, voiced_ratio=0.075
            ),
        )
        == ""
    )


def test_sanitize_stt_text_keeps_suspicious_phrase_without_audio_context() -> None:
    assert sanitize_stt_text("시청해주셔서 감사합니다") == "시청해주셔서 감사합니다"


def test_sanitize_stt_text_keeps_real_speech_even_if_phrase_is_known() -> None:
    assert (
        sanitize_stt_text(
            "시청해주셔서 감사합니다",
            analysis=_analysis(peak_rms=0.32, voiced_ms=1100, voiced_ratio=0.52),
        )
        == "시청해주셔서 감사합니다"
    )


def test_sanitize_stt_text_filters_placeholder_symbols() -> None:
    assert sanitize_stt_text("?? ?? ?? ??") == ""


def test_audiosegment_to_mono_float32_normalizes_samples() -> None:
    segment = (
        AudioSegment.silent(duration=100, frame_rate=16000).set_sample_width(2).set_channels(1)
    )

    samples, sample_rate = _audiosegment_to_mono_float32(segment)

    assert sample_rate == 16000
    assert samples.dtype == np.float32
    assert np.max(np.abs(samples)) <= 1.0


def test_is_no_speech_audio_file_detects_silence(tmp_path) -> None:
    path = tmp_path / "silence.wav"
    AudioSegment.silent(duration=3000, frame_rate=16000).export(path, format="wav")

    assert is_no_speech_audio_file(str(path)) is True


def test_is_no_speech_audio_file_keeps_voice_like_audio(tmp_path) -> None:
    path = tmp_path / "voice.wav"
    tone = AudioSegment.silent(duration=2000, frame_rate=16000) + Sine(220).to_audio_segment(
        duration=1000,
        volume=-12,
    ).set_frame_rate(16000)
    tone.export(path, format="wav")

    assert is_no_speech_audio_file(str(path)) is False


def test_speech_to_text_skips_transcription_when_audio_is_no_speech() -> None:
    with (
        patch(
            "app.services.stt_service.analyze_no_speech_audio_file",
            return_value=_analysis(is_no_speech=True),
        ),
        patch("app.services.stt_service.transcribe_audio_file") as transcribe_mock,
    ):
        result = speech_to_text("silent.webm")

    assert result == ""
    transcribe_mock.assert_not_called()
