import base64
from io import BytesIO
from unittest.mock import patch

from app.core import settings
from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)
AUTH_TOKEN = settings.AUTH_TOKENS.split("|", 1)[0]
NEW_USER_QUESTION = "Hello tester, thanks for joining today."


def auth_headers() -> dict[str, str]:
    return {"Authorization": f"Bearer {AUTH_TOKEN}"}


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
    }


def build_pipeline_prompt_response() -> dict[str, object]:
    return {
        **build_interview_state_prompt_response(),
        "formatted_prompt": "[internal-only prompt]",
    }


def build_pipeline_response(stt_text: str) -> dict[str, object]:
    return {
        "stt_text": stt_text,
        "masked_stt_text": "candidate [PHONE_1]",
        "pii_masking_duration_seconds": 0.0012,
        "interview_state_db": build_interview_state_db_response(),
        "interview_state_prompt": build_pipeline_prompt_response(),
        "question_text": "next question",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/wav",
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
            json={
                "nickname": "tester",
                "is_first_finished": False,
            },
            headers=auth_headers(),
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
            json={
                "nickname": "tester",
                "is_first_finished": True,
            },
            headers=auth_headers(),
        )

    assert response.status_code == 400
    assert response.json() == {"detail": "db_question is required when is_first_finished is true."}


def test_interview_pipeline_returns_follow_up_tts_and_state_payloads() -> None:
    with patch(
        "app.api.v1.endpoints.interview.run_interview_pipeline_from_upload",
        return_value=build_pipeline_response("candidate answer"),
    ) as pipeline_mock:
        response = client.post(
            "/api/v1/ai/interview/pipeline",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            data={"is_first_finished": "false", "max_tokens": "256"},
            headers=auth_headers(),
        )

    assert response.status_code == 200
    assert response.json() == build_pipeline_response("candidate answer")
    pipeline_mock.assert_called_once()
    assert pipeline_mock.call_args.kwargs["voice"] == "Robert"


def test_interview_pipeline_after_stt_returns_follow_up_tts_and_state_payloads() -> None:
    with patch(
        "app.api.v1.endpoints.interview.run_interview_pipeline_from_stt_payload",
        return_value=build_pipeline_response("candidate answer from external stt"),
    ) as pipeline_mock:
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
            headers=auth_headers(),
        )

    assert response.status_code == 200
    assert response.json() == build_pipeline_response("candidate answer from external stt")
    pipeline_mock.assert_called_once()
    assert pipeline_mock.call_args.kwargs["voice"] == "F3"
    assert pipeline_mock.call_args.kwargs["interview_id"] is None
    assert pipeline_mock.call_args.kwargs["question_id"] is None


def test_interview_pipeline_after_stt_returns_bad_request_when_text_is_missing() -> None:
    response = client.post(
        "/api/v1/ai/interview/pipeline/after-stt",
        files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
        data={"stt_text": "   ", "nickname": "tester"},
        headers=auth_headers(),
    )

    assert response.status_code == 400
    assert response.json() == {"detail": "stt_text is required."}


def test_interview_pipeline_rejects_non_webm_upload() -> None:
    response = client.post(
        "/api/v1/ai/interview/pipeline",
        files={"audio_file": ("answer.wav", BytesIO(b"wav-audio"), "audio/wav")},
        headers=auth_headers(),
    )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "Only .webm audio uploads are supported.",
    }


def test_interview_state_temp_save_returns_wrapped_request() -> None:
    response = client.post(
        "/api/v1/ai/interview/state/temp-save",
        json=build_interview_state_db_response(),
        headers=auth_headers(),
    )

    assert response.status_code == 200
    assert response.json() == {
        "status": "queued",
        "message": "Temporary interview-state save request accepted.",
        "request": build_interview_state_db_response(),
    }


def test_interview_refine_answer_returns_refined_text() -> None:
    with patch(
        "app.api.v1.endpoints.interview.refine_answer_for_magazine",
        return_value={"refined_answer": "refined answer"},
    ) as refine_mock:
        response = client.post(
            "/api/v1/ai/interview/refine-answer",
            json={
                "stt_text": "I was nervous at first, but I adapted quickly.",
            },
            headers=auth_headers(),
        )

    assert response.status_code == 200
    assert response.json() == {
        "refined_answer": "refined answer",
    }
    refine_mock.assert_called_once_with(
        stt_text="I was nervous at first, but I adapted quickly.",
    )


def test_interview_refine_answer_returns_bad_request_when_text_is_blank() -> None:
    response = client.post(
        "/api/v1/ai/interview/refine-answer",
        json={"stt_text": "   "},
        headers=auth_headers(),
    )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "stt_text is required.",
    }


def test_interview_state_analysis_returns_state_payloads() -> None:
    with patch(
        "app.api.v1.endpoints.interview.analyze_interview_state_from_payload",
        return_value={
            "interview_state_db": build_interview_state_db_response(),
        },
    ) as analysis_mock:
        response = client.post(
            "/api/v1/ai/interview/state-analysis",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            data={"stt_text": "candidate answer"},
            headers=auth_headers(),
        )

    assert response.status_code == 200
    assert response.json() == {
        "interview_state_db": build_interview_state_db_response(),
    }
    analysis_mock.assert_called_once()


def test_interview_state_analysis_returns_bad_request_when_text_is_blank() -> None:
    response = client.post(
        "/api/v1/ai/interview/state-analysis",
        files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
        data={"stt_text": "   "},
        headers=auth_headers(),
    )

    assert response.status_code == 400
    assert response.json() == {"detail": "Only .webm audio uploads are supported."}
