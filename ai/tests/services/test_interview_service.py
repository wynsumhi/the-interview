import base64
from io import BytesIO
from unittest.mock import patch

import pytest
from app.services.interview_service import (
    analyze_interview_state_from_payload,
    run_interview_pipeline_from_upload,
    save_interview_state_temp,
)
from fastapi import UploadFile


def build_interview_state_db_response() -> dict[str, object]:
    return {
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
    }


def build_pipeline_prompt_response() -> dict[str, object]:
    return {
        **build_interview_state_prompt_response(),
        "formatted_prompt": "[internal-only prompt]",
    }


def test_run_interview_pipeline_from_upload_returns_encoded_audio() -> None:
    upload = UploadFile(filename="answer.webm", file=BytesIO(b"audio-bytes"))

    with patch("app.services.interview_service.run_interview_pipeline") as pipeline_mock:
        pipeline_mock.return_value.to_response_dict.return_value = {
            "stt_text": "candidate answer",
            "masked_stt_text": "candidate [PHONE_1]",
            "pii_masking_duration_seconds": 0.0012,
            "interview_state_db": build_interview_state_db_response(),
            "interview_state_prompt": build_pipeline_prompt_response(),
            "question_text": "follow up",
            "tts_audio": b"tts-bytes",
            "refined_answer": "refined answer",
        }

        result = run_interview_pipeline_from_upload(
            upload,
            user_id="user-123",
            previous_question="current question",
            max_tokens=256,
        )

    assert result == {
        "stt_text": "candidate answer",
        "masked_stt_text": "candidate [PHONE_1]",
        "pii_masking_duration_seconds": 0.0012,
        "interview_state_db": build_interview_state_db_response(),
        "interview_state_prompt": build_pipeline_prompt_response(),
        "question_text": "follow up",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/wav",
        "refined_answer": "refined answer",
    }
    pipeline_mock.assert_called_once()
    args, kwargs = pipeline_mock.call_args
    assert args
    assert kwargs == {
        "is_first_finished": False,
        "user_id": "user-123",
        "sequence": None,
        "nickname": None,
        "previous_question": "current question",
        "max_tokens": 256,
    }


def test_run_interview_pipeline_from_upload_rejects_invalid_extension() -> None:
    upload = UploadFile(filename="answer.wav", file=BytesIO(b"audio-bytes"))

    with pytest.raises(ValueError, match="Only \\.webm audio uploads are supported\\."):
        run_interview_pipeline_from_upload(upload)


def test_save_interview_state_temp_wraps_request() -> None:
    request = build_interview_state_db_response()

    result = save_interview_state_temp(request)

    assert result == {
        "status": "queued",
        "message": "Temporary interview-state save request accepted.",
        "request": request,
    }


def test_analyze_interview_state_from_payload_returns_state_only() -> None:
    upload = UploadFile(filename="answer.webm", file=BytesIO(b"audio-bytes"))
    prompt_payload = type(
        "PromptPayload",
        (),
        {
            "top_state": "calm",
            "fused_scores": build_interview_state_prompt_response()["fused_scores"],
            "formatted_prompt": "[internal-only prompt]",
        },
    )()

    with (
        patch(
            "app.services.interview_service.analyze_interview_state",
            return_value=object(),
        ) as analyze_mock,
        patch(
            "app.services.interview_service.build_interview_state_db_payload",
            return_value=build_interview_state_db_response(),
        ) as db_mock,
        patch(
            "app.services.interview_service.build_interview_state_prompt_payload",
            return_value=prompt_payload,
        ) as prompt_mock,
    ):
        result = analyze_interview_state_from_payload(
            audio_file=upload,
            stt_text="candidate answer",
        )

    assert result == {
        "interview_state_db": build_interview_state_db_response(),
        "interview_state_prompt": build_interview_state_prompt_response(),
    }
    analyze_mock.assert_called_once()
    db_mock.assert_called_once_with(analyze_mock.return_value)
    prompt_mock.assert_called_once_with(analyze_mock.return_value)


def test_analyze_interview_state_from_payload_rejects_blank_stt_text() -> None:
    upload = UploadFile(filename="answer.webm", file=BytesIO(b"audio-bytes"))

    with pytest.raises(ValueError, match="stt_text is required\\."):
        analyze_interview_state_from_payload(
            audio_file=upload,
            stt_text="   ",
        )
