import base64
from io import BytesIO
from unittest.mock import patch

from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)
NEW_USER_QUESTION = "안녕하세요 민수님, 오늘 시간 내주셔서 감사합니다. 요즘 어떻게 지내시고 계세요?"


def build_interview_state_db_response() -> dict[str, object]:
    return {
        "answer_id": 123,
        "top_state": "calm",
        "fused_scores": {
            "calm": 0.75,
            "tense": 0.25,
        },
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


def build_temp_interview_state_db_response() -> dict[str, object]:
    response = build_interview_state_db_response()
    response["answer_id"] = 0
    return response


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
        "formatted_prompt": (
            "[인터뷰 상태 참고]\n"
            "다음 점수는 답변 음성과 텍스트를 바탕으로 계산된 참고용 상태 신호입니다. "
            "절대 판단이 아니라 후속 질문의 톤과 깊이를 조절하는 보조 정보로만 활용하세요.\n"
            "- top_state: calm\n"
            "- fused_scores: calm=0.75, tense=0.25, anxious=0.00, low=0.00, engaged=0.00"
        ),
    }


def test_interview_start_tts_returns_new_user_question_and_audio() -> None:
    with patch(
        "app.api.v1.endpoints.interview.generate_interview_start_tts",
        return_value={
            "question_text": NEW_USER_QUESTION,
            "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
            "tts_audio_mime_type": "audio/wav",
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
        "tts_audio_mime_type": "audio/wav",
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


def test_interview_pipeline_returns_follow_up_tts_and_state_payloads() -> None:
    with patch(
        "app.api.v1.endpoints.interview.run_interview_pipeline_from_upload",
        return_value={
            "stt_text": "candidate answer",
            "interview_state_db": build_interview_state_db_response(),
            "interview_state_prompt": build_interview_state_prompt_response(),
            "question_text": "next question",
            "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
            "tts_audio_mime_type": "audio/wav",
        },
    ) as pipeline_mock:
        response = client.post(
            "/api/v1/ai/interview/pipeline",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            data={"answer_id": "123", "is_first_finished": "false", "max_tokens": "256"},
        )

    assert response.status_code == 200
    assert response.json() == {
        "stt_text": "candidate answer",
        "interview_state_db": build_interview_state_db_response(),
        "interview_state_prompt": build_interview_state_prompt_response(),
        "question_text": "next question",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/wav",
    }
    pipeline_mock.assert_called_once()


def test_interview_pipeline_after_stt_returns_follow_up_tts_and_state_payloads() -> None:
    with patch(
        "app.api.v1.endpoints.interview.run_interview_pipeline_from_stt_payload",
        return_value={
            "stt_text": "candidate answer from external stt",
            "interview_state_db": build_temp_interview_state_db_response(),
            "interview_state_prompt": build_interview_state_prompt_response(),
            "question_text": "next question",
            "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
            "tts_audio_mime_type": "audio/wav",
        },
    ) as pipeline_mock:
        response = client.post(
            "/api/v1/ai/interview/pipeline/after-stt",
            files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
            data={
                "stt_text": "candidate answer from external stt",
                "is_first_finished": "true",
                "nickname": "민수",
                "max_tokens": "256",
            },
        )

    assert response.status_code == 200
    assert response.json() == {
        "stt_text": "candidate answer from external stt",
        "interview_state_db": build_temp_interview_state_db_response(),
        "interview_state_prompt": build_interview_state_prompt_response(),
        "question_text": "next question",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/wav",
    }
    pipeline_mock.assert_called_once()


def test_interview_pipeline_after_stt_returns_bad_request_when_text_is_missing() -> None:
    response = client.post(
        "/api/v1/ai/interview/pipeline/after-stt",
        files={"audio_file": ("answer.webm", BytesIO(b"webm-audio"), "audio/webm")},
        data={"stt_text": "   ", "nickname": "민수"},
    )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "stt_text is required.",
    }


def test_interview_pipeline_rejects_non_webm_upload() -> None:
    response = client.post(
        "/api/v1/ai/interview/pipeline",
        files={"audio_file": ("answer.wav", BytesIO(b"wav-audio"), "audio/wav")},
        data={"answer_id": "123"},
    )

    assert response.status_code == 400
    assert response.json() == {
        "detail": "Only .webm audio uploads are supported.",
    }


def test_interview_state_temp_save_returns_wrapped_request() -> None:
    response = client.post(
        "/api/v1/ai/interview/state/temp-save",
        json=build_interview_state_db_response(),
    )

    assert response.status_code == 200
    assert response.json() == {
        "status": "queued",
        "message": "Temporary interview-state save request accepted.",
        "request": build_interview_state_db_response(),
    }
