from io import BytesIO
from unittest.mock import patch

from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)


def test_stt_endpoint_returns_transcription() -> None:
    with patch(
        "app.api.v1.endpoints.stt.speech_to_text_from_upload",
        return_value={"stt_text": "candidate answer"},
    ) as stt_mock:
        response = client.post(
            "/api/v1/ai/stt",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
        )

    assert response.status_code == 200
    assert response.json() == {"stt_text": "candidate answer"}
    stt_mock.assert_called_once()


def test_stt_endpoint_rejects_invalid_extension() -> None:
    with patch(
        "app.api.v1.endpoints.stt.speech_to_text_from_upload",
        side_effect=ValueError("Only .webm audio uploads are supported."),
    ):
        response = client.post(
            "/api/v1/ai/stt",
            files={"audio_file": ("answer.wav", BytesIO(b"wav-audio"), "audio/wav")},
        )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "Only .webm audio uploads are supported.",
    }
