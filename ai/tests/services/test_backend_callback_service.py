from types import SimpleNamespace
from unittest.mock import Mock, patch

from app.services import backend_callback_service


def test_is_refined_answer_callback_enabled_returns_true_when_url_and_token_exist() -> None:
    with patch.object(
        backend_callback_service,
        "settings",
        SimpleNamespace(
            BACKEND_INTERNAL_BASE_URL="http://localhost:8080",
            BACKEND_INTERNAL_TOKEN="secret-token",
            BACKEND_INTERNAL_TIMEOUT_SECONDS=5.0,
        ),
    ):
        assert backend_callback_service.is_refined_answer_callback_enabled() is True


def test_is_refined_answer_callback_enabled_returns_false_when_url_is_missing() -> None:
    with patch.object(
        backend_callback_service,
        "settings",
        SimpleNamespace(
            BACKEND_INTERNAL_BASE_URL="",
            BACKEND_INTERNAL_TOKEN="secret-token",
            BACKEND_INTERNAL_TIMEOUT_SECONDS=5.0,
        ),
    ):
        assert backend_callback_service.is_refined_answer_callback_enabled() is False


def test_save_refined_answer_to_backend_uses_backend_contract() -> None:
    response = Mock()
    response.raise_for_status = Mock()

    with (
        patch.object(
            backend_callback_service,
            "settings",
            SimpleNamespace(
                BACKEND_INTERNAL_BASE_URL="https://j14a307.p.ssafy.io",
                BACKEND_INTERNAL_TOKEN="secret-token",
                BACKEND_INTERNAL_TIMEOUT_SECONDS=5.0,
            ),
        ),
        patch(
            "app.services.backend_callback_service.requests.post", return_value=response
        ) as post_mock,
    ):
        result = backend_callback_service.save_refined_answer_to_backend(
            interview_id=1,
            question_id=2,
            refined_answer="정제된 답변",
        )

    assert result is True
    post_mock.assert_called_once_with(
        "https://j14a307.p.ssafy.io/v1/interview/internal/answers/refined",
        json={
            "interviewId": 1,
            "questionId": 2,
            "refinedAnswer": "정제된 답변",
        },
        headers={"Authorization": "Bearer secret-token"},
        timeout=5.0,
    )
    response.raise_for_status.assert_called_once_with()
