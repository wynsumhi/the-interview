import base64
from io import BytesIO
from unittest.mock import patch

from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)


def test_interview_pipeline_returns_follow_up_and_tts_audio() -> None:
    with patch(
        "app.api.v1.endpoints.interview.run_interview_pipeline_from_upload",
        return_value={
            "stt_text": "candidate answer",
            "question_text": "next question",
            "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
            "tts_audio_mime_type": "audio/mpeg",
        },
    ) as pipeline_mock:
        response = client.post(
            "/api/v1/ai/interviews/pipeline",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            data={"max_tokens": "256"},
        )

    assert response.status_code == 200
    assert response.json() == {
        "stt_text": "candidate answer",
        "question_text": "next question",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/mpeg",
    }
    pipeline_mock.assert_called_once()


def test_interview_pipeline_rejects_non_webm_upload() -> None:
    response = client.post(
        "/api/v1/ai/interviews/pipeline",
        files={"audio_file": ("answer.wav", BytesIO(b"wav-audio"), "audio/wav")},
    )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "Only .webm audio uploads are supported.",
    }
