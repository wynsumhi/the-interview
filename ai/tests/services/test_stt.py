from io import BytesIO
from unittest.mock import patch

import pytest
from app.services.stt_service import speech_to_text_from_upload, validate_audio_upload
from fastapi import UploadFile


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
