from io import BytesIO
from unittest.mock import patch

from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)


def test_health_endpoint_stays_public_when_auth_is_enabled() -> None:
    with (
        patch("app.core.auth.settings.AUTH_ENABLED", True),
        patch("app.core.auth.settings.AUTH_TOKENS", "client-token|ai:invoke"),
    ):
        response = client.get("/api/v1/ai/health")

    assert response.status_code == 200


def test_interview_endpoint_requires_bearer_token_when_auth_is_enabled() -> None:
    with (
        patch("app.core.auth.settings.AUTH_ENABLED", True),
        patch("app.core.auth.settings.AUTH_TOKENS", "client-token|ai:invoke"),
    ):
        response = client.post(
            "/api/v1/ai/interview/start-tts",
            json={"nickname": "tester", "is_first_finished": False},
        )

    assert response.status_code == 401
    assert response.json() == {"detail": "Missing bearer token."}


def test_interview_endpoint_rejects_token_without_required_scope() -> None:
    with (
        patch("app.core.auth.settings.AUTH_ENABLED", True),
        patch("app.core.auth.settings.AUTH_TOKENS", "client-token|stt:invoke"),
    ):
        response = client.post(
            "/api/v1/ai/interview/start-tts",
            json={"nickname": "tester", "is_first_finished": False},
            headers={"Authorization": "Bearer client-token"},
        )

    assert response.status_code == 403
    assert response.json() == {"detail": "Missing required scope: ai:invoke"}


def test_interview_endpoint_accepts_valid_token_scope() -> None:
    with (
        patch("app.core.auth.settings.AUTH_ENABLED", True),
        patch("app.core.auth.settings.AUTH_TOKENS", "client-token|ai:invoke"),
        patch(
            "app.api.v1.endpoints.interview.generate_interview_start_tts",
            return_value={
                "question_text": "question",
                "tts_audio_base64": "dHRz",
                "tts_audio_mime_type": "audio/wav",
            },
        ),
    ):
        response = client.post(
            "/api/v1/ai/interview/start-tts",
            json={"nickname": "tester", "is_first_finished": False},
            headers={"Authorization": "Bearer client-token"},
        )

    assert response.status_code == 200


def test_stt_endpoint_accepts_wildcard_scope_token() -> None:
    with (
        patch("app.core.auth.settings.AUTH_ENABLED", True),
        patch("app.core.auth.settings.AUTH_TOKENS", "admin-token|*"),
        patch(
            "app.api.v1.endpoints.stt.speech_to_text_from_upload",
            return_value={"stt_text": "candidate answer"},
        ),
    ):
        response = client.post(
            "/api/v1/ai/stt",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            headers={"Authorization": "Bearer admin-token"},
        )

    assert response.status_code == 200
