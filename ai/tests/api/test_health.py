from unittest.mock import patch

from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)


def test_root_returns_service_name() -> None:
    response = client.get("/")

    assert response.status_code == 200
    assert response.json() == {"message": "The Interview AI API"}


def test_health_check_returns_ok() -> None:
    response = client.get("/api/v1/ai/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "ai-fastapi",
    }


def test_app_startup_preloads_tts_when_enabled() -> None:
    with (
        patch("app.main.DEFAULT_TTS_PRELOAD_ON_STARTUP", True),
        patch(
            "app.main.warm_up_speech_engine",
        ) as warm_up_mock,
    ):
        with TestClient(app):
            pass

    warm_up_mock.assert_called_once_with()
