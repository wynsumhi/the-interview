from io import BytesIO
from unittest.mock import patch

import numpy as np
import pytest
from app.services.stt_service import (
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


def test_validate_audio_upload_rejects_non_webm() -> None:
    # 현재 서비스 계약은 webm 업로드만 허용하므로 확장자 가드가 유지되는지 본다.
    upload = UploadFile(filename="answer.wav", file=BytesIO(b"wav-audio"))

    with pytest.raises(ValueError, match="Only \\.webm audio uploads are supported\\."):
        validate_audio_upload(upload)


def test_speech_to_text_from_upload_returns_transcription() -> None:
    # 업로드 파일 저장/정리와 상관없이 서비스 결과가 그대로 API 응답 형태로 감싸지는지 확인한다.
    upload = UploadFile(filename="answer.webm", file=BytesIO(b"webm-audio"))

    with patch(
        "app.services.stt_service.speech_to_text",
        return_value="transcribed text",
    ) as stt_mock:
        result = speech_to_text_from_upload(upload)

    assert result == {"stt_text": "transcribed text"}
    stt_mock.assert_called_once()


def test_sanitize_stt_text_filters_known_silence_hallucination() -> None:
    # 실제로 관찰된 방송형 hallucination은 빈 문자열로 치환되어야 한다.
    assert sanitize_stt_text("MBC뉴스 이덕영입니다. 시청해주셔서 감사합니다.") == ""


def test_sanitize_stt_text_keeps_normal_interview_answer() -> None:
    # 정상 답변은 공백 정리만 하고 내용은 보존해야 한다.
    assert (
        sanitize_stt_text(" 저는 백엔드 성능 개선을 맡았습니다. ")
        == "저는 백엔드 성능 개선을 맡았습니다."
    )


def test_sanitize_stt_text_filters_placeholder_symbols() -> None:
    # 프롬프트를 준 뒤에도 "???" 같은 placeholder가 나오는 경우가 있어 별도 방어가 필요하다.
    assert sanitize_stt_text("?? ?? ?? ??") == ""


def test_audiosegment_to_mono_float32_normalizes_samples() -> None:
    # no-speech 분석 전 정규화 함수가 기대한 dtype/범위로 변환하는지 확인한다.
    segment = (
        AudioSegment.silent(duration=100, frame_rate=16000).set_sample_width(2).set_channels(1)
    )

    samples, sample_rate = _audiosegment_to_mono_float32(segment)

    assert sample_rate == 16000
    assert samples.dtype == np.float32
    assert np.max(np.abs(samples)) <= 1.0


def test_is_no_speech_audio_file_detects_silence(tmp_path) -> None:
    # 완전 무음은 반드시 no-speech로 판정되어 STT 호출이 스킵되어야 한다.
    path = tmp_path / "silence.wav"
    AudioSegment.silent(duration=3000, frame_rate=16000).export(path, format="wav")

    assert is_no_speech_audio_file(str(path)) is True


def test_is_no_speech_audio_file_keeps_voice_like_audio(tmp_path) -> None:
    # 뒤쪽에 실제 에너지가 있는 구간이 있으면 no-speech로 잘못 자르면 안 된다.
    path = tmp_path / "voice.wav"
    tone = AudioSegment.silent(duration=2000, frame_rate=16000) + Sine(220).to_audio_segment(
        duration=1000,
        volume=-12,
    ).set_frame_rate(16000)
    tone.export(path, format="wav")

    assert is_no_speech_audio_file(str(path)) is False


def test_speech_to_text_skips_transcription_when_audio_is_no_speech() -> None:
    # no-speech 게이트가 True를 반환하면 외부 STT 호출이 완전히 생략되어야 한다.
    with (
        patch("app.services.stt_service.is_no_speech_audio_file", return_value=True),
        patch("app.services.stt_service.transcribe_audio_file") as transcribe_mock,
    ):
        result = speech_to_text("silent.webm")

    assert result == ""
    transcribe_mock.assert_not_called()
