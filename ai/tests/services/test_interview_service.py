import base64
from io import BytesIO
from unittest.mock import patch

import pytest
from app.services.interview_service import (
    run_interview_pipeline_from_upload,
    save_interview_state_temp,
)
from fastapi import UploadFile


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


def test_run_interview_pipeline_from_upload_returns_encoded_audio() -> None:
    upload = UploadFile(filename="answer.webm", file=BytesIO(b"audio-bytes"))

    with patch("app.services.interview_service.run_interview_pipeline") as pipeline_mock:
        pipeline_mock.return_value.to_response_dict.return_value = {
            "stt_text": "candidate answer",
            "interview_state_db": build_interview_state_db_response(),
            "interview_state_prompt": build_interview_state_prompt_response(),
            "question_text": "follow up",
            "tts_audio": b"tts-bytes",
        }

        result = run_interview_pipeline_from_upload(upload, answer_id=123, max_tokens=256)

    assert result == {
        "stt_text": "candidate answer",
        "interview_state_db": build_interview_state_db_response(),
        "interview_state_prompt": build_interview_state_prompt_response(),
        "question_text": "follow up",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/wav",
    }
    pipeline_mock.assert_called_once()


def test_run_interview_pipeline_from_upload_rejects_invalid_extension() -> None:
    upload = UploadFile(filename="answer.wav", file=BytesIO(b"audio-bytes"))

    with pytest.raises(ValueError, match="Only \\.webm audio uploads are supported\\."):
        run_interview_pipeline_from_upload(upload, answer_id=123)


def test_save_interview_state_temp_wraps_request() -> None:
    request = build_interview_state_db_response()

    result = save_interview_state_temp(request)

    assert result == {
        "status": "queued",
        "message": "Temporary interview-state save request accepted.",
        "request": request,
    }
