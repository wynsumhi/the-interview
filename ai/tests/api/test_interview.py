import base64
from io import BytesIO
from unittest.mock import patch

from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)
NEW_USER_QUESTION = "안녕하세요. 민수님. 오늘 시간 내주셔서 감사해요. 요즘 어떻게 지내고 계시나요?"


def test_interview_start_tts_returns_new_user_question_and_audio() -> None:
    with patch(
        "app.api.v1.endpoints.interview.generate_interview_start_tts",
        return_value={
            "question_text": NEW_USER_QUESTION,
            "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
            "tts_audio_mime_type": "audio/mpeg",
        },
    ) as start_tts_mock:
        response = client.post(
            "/api/v1/ai/interview/start-tts",
            json={
                "nickname": "민수",
                "is_first_finished": False,
            },
        )

    assert response.status_code == 200
    assert response.json() == {
        "question_text": NEW_USER_QUESTION,
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/mpeg",
    }
    start_tts_mock.assert_called_once_with(
        is_first_finished=False,
        nickname="민수",
        db_question=None,
    )


def test_interview_start_tts_returns_bad_request_when_service_validation_fails() -> None:
    with patch(
        "app.api.v1.endpoints.interview.generate_interview_start_tts",
        side_effect=ValueError("db_question is required when is_first_finished is true."),
    ):
        response = client.post(
            "/api/v1/ai/interview/start-tts",
            json={
                "nickname": "민수",
                "is_first_finished": True,
            },
        )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "db_question is required when is_first_finished is true.",
    }


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
            "/api/v1/ai/interview/pipeline",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            data={"is_first_finished": "false", "max_tokens": "256"},
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
        "/api/v1/ai/interview/pipeline",
        files={"audio_file": ("answer.wav", BytesIO(b"wav-audio"), "audio/wav")},
    )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "Only .webm audio uploads are supported.",
    }
