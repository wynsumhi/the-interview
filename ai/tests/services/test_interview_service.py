import base64
from io import BytesIO
from unittest.mock import patch

import pytest
from app.services.interview_service import run_interview_pipeline_from_upload
from fastapi import UploadFile


def test_run_interview_pipeline_from_upload_returns_encoded_audio() -> None:
    upload = UploadFile(filename="answer.webm", file=BytesIO(b"audio-bytes"))

    with patch("app.services.interview_service.run_interview_pipeline") as pipeline_mock:
        pipeline_mock.return_value.stt_text = "candidate answer"
        pipeline_mock.return_value.llm_text = "follow up"
        pipeline_mock.return_value.tts_audio = b"tts-bytes"

        result = run_interview_pipeline_from_upload(upload, max_tokens=256)

    assert result == {
        "question_text": "follow up",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/mpeg",
    }
    pipeline_mock.assert_called_once()


def test_run_interview_pipeline_from_upload_rejects_invalid_extension() -> None:
    upload = UploadFile(filename="answer.wav", file=BytesIO(b"audio-bytes"))

    with pytest.raises(ValueError, match="Only \\.webm audio uploads are supported\\."):
        run_interview_pipeline_from_upload(upload)
