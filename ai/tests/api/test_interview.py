import base64
from io import BytesIO
from unittest.mock import patch

from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)
NEW_USER_QUESTION = "Welcome question"


def build_interview_state_db_response() -> dict[str, object]:
    return {
        "top_state": "calm",
        "fused_scores": {"calm": 0.75, "tense": 0.25},
        "audio_features": {
            "duration_sec": 1.0,
            "rms_mean": 0.02,
            "rms_std": 0.01,
            "zcr_mean": 0.1,
            "silence_ratio": 0.2,
            "voiced_ratio": 0.8,
            "speech_rate_proxy": 0.8,
            "pitch_mean_hz": 120.0,
            "pitch_std_hz": 10.0,
        },
    }


def build_interview_state_prompt_response() -> dict[str, object]:
    return {
        "top_state": "calm",
        "fused_scores": {
            "calm": 0.75,
            "tense": 0.25,
            "anxious": 0.0,
            "low": 0.0,
            "engaged": 0.0,
        },
        "formatted_prompt": "[state prompt]",
    }


def test_interview_start_tts_returns_new_user_question_and_audio() -> None:
    with (
        patch("app.core.auth.settings.AUTH_ENABLED", False),
        patch(
            "app.api.v1.endpoints.interview.generate_interview_start_tts",
            return_value={
                "question_text": NEW_USER_QUESTION,
                "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
                "tts_audio_mime_type": "audio/wav",
            },
        ) as start_tts_mock,
    ):
        response = client.post(
            "/api/v1/ai/interview/start-tts",
            json={"nickname": "tester", "is_first_finished": False, "voice": "Jessica"},
        )

    assert response.status_code == 200
    assert response.json() == {
        "question_text": NEW_USER_QUESTION,
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/wav",
    }
    start_tts_mock.assert_called_once_with(
        is_first_finished=False,
        nickname="tester",
        db_question=None,
        voice="Jessica",
    )


def test_interview_start_tts_returns_bad_request_when_service_validation_fails() -> None:
    with (
        patch("app.core.auth.settings.AUTH_ENABLED", False),
        patch(
            "app.api.v1.endpoints.interview.generate_interview_start_tts",
            side_effect=ValueError("db_question is required when is_first_finished is true."),
        ),
    ):
        response = client.post(
            "/api/v1/ai/interview/start-tts",
            json={"nickname": "tester", "is_first_finished": True},
        )

    assert response.status_code == 400
    assert response.json() == {"detail": "db_question is required when is_first_finished is true."}


def test_interview_pipeline_returns_follow_up_tts_and_state_payloads() -> None:
    with (
        patch("app.core.auth.settings.AUTH_ENABLED", False),
        patch(
            "app.api.v1.endpoints.interview.run_interview_pipeline_from_upload",
            return_value={
                "stt_text": "candidate answer",
                "masked_stt_text": "candidate [PHONE_1]",
                "pii_masking_duration_seconds": 0.0012,
                "interview_state_db": build_interview_state_db_response(),
                "interview_state_prompt": build_interview_state_prompt_response(),
                "question_text": "next question",
                "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
                "tts_audio_mime_type": "audio/wav",
                "refined_answer": "refined answer",
            },
        ) as pipeline_mock,
    ):
        response = client.post(
            "/api/v1/ai/interview/pipeline",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            data={"is_first_finished": "false", "voice": "Robert", "max_tokens": "256"},
        )

    assert response.status_code == 200
    assert response.json() == {
        "stt_text": "candidate answer",
        "masked_stt_text": "candidate [PHONE_1]",
        "pii_masking_duration_seconds": 0.0012,
        "interview_state_db": build_interview_state_db_response(),
        "interview_state_prompt": build_interview_state_prompt_response(),
        "question_text": "next question",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/wav",
        "refined_answer": "refined answer",
    }
    pipeline_mock.assert_called_once()
    assert pipeline_mock.call_args.kwargs["voice"] == "Robert"


def test_interview_pipeline_after_stt_returns_follow_up_tts_and_state_payloads() -> None:
    with (
        patch("app.core.auth.settings.AUTH_ENABLED", False),
        patch(
            "app.api.v1.endpoints.interview.run_interview_pipeline_from_stt_payload",
            return_value={
                "stt_text": "candidate answer from external stt",
                "masked_stt_text": "candidate [EMAIL_1]",
                "pii_masking_duration_seconds": 0.0012,
                "interview_state_db": build_interview_state_db_response(),
                "interview_state_prompt": build_interview_state_prompt_response(),
                "question_text": "next question",
                "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
                "tts_audio_mime_type": "audio/wav",
                "refined_answer": "refined answer",
            },
        ) as pipeline_mock,
    ):
        response = client.post(
            "/api/v1/ai/interview/pipeline/after-stt",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            data={
                "stt_text": "candidate answer from external stt",
                "is_first_finished": "true",
                "voice": "F3",
                "nickname": "tester",
                "max_tokens": "256",
            },
        )

    assert response.status_code == 200
    assert response.json() == {
        "stt_text": "candidate answer from external stt",
        "masked_stt_text": "candidate [EMAIL_1]",
        "pii_masking_duration_seconds": 0.0012,
        "interview_state_db": build_interview_state_db_response(),
        "interview_state_prompt": build_interview_state_prompt_response(),
        "question_text": "next question",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/wav",
        "refined_answer": "refined answer",
    }
    pipeline_mock.assert_called_once()
    assert pipeline_mock.call_args.kwargs["voice"] == "F3"


def test_interview_pipeline_after_stt_returns_bad_request_when_text_is_missing() -> None:
    with patch("app.core.auth.settings.AUTH_ENABLED", False):
        response = client.post(
            "/api/v1/ai/interview/pipeline/after-stt",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            data={"stt_text": "   ", "nickname": "tester"},
        )

    assert response.status_code == 400
    assert response.json() == {"detail": "stt_text is required."}


def test_interview_pipeline_rejects_non_webm_upload() -> None:
    with patch("app.core.auth.settings.AUTH_ENABLED", False):
        response = client.post(
            "/api/v1/ai/interview/pipeline",
            files={"audio_file": ("answer.wav", BytesIO(b"wav-audio"), "audio/wav")},
        )

    assert response.status_code == 400
    assert response.json() == {"detail": "Only .webm audio uploads are supported."}
