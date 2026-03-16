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
