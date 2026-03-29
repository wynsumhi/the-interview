import base64
from unittest.mock import patch

import pytest
from app.services.interview_service import (
    build_interview_start_question_text,
    generate_interview_start_tts,
)


def test_build_interview_start_question_text_for_new_user() -> None:
    result = build_interview_start_question_text(
        is_first_finished=False,
        nickname="tester",
    )

    assert "tester" in result


def test_build_interview_start_question_text_for_existing_user() -> None:
    result = build_interview_start_question_text(
        is_first_finished=True,
        db_question="Saved DB question",
    )

    assert result == "Saved DB question"


@pytest.mark.parametrize(
    ("is_first_finished", "nickname", "db_question", "message"),
    [
        (False, None, None, "nickname is required when is_first_finished is false."),
        (True, "tester", None, "db_question is required when is_first_finished is true."),
    ],
)
def test_build_interview_start_question_text_validates_required_inputs(
    is_first_finished: bool,
    nickname: str | None,
    db_question: str | None,
    message: str,
) -> None:
    with pytest.raises(ValueError, match=message):
        build_interview_start_question_text(
            is_first_finished=is_first_finished,
            nickname=nickname,
            db_question=db_question,
        )


def test_generate_interview_start_tts_returns_base64_audio() -> None:
    with patch(
        "app.services.interview_service.text_to_speech",
        return_value=b"tts-bytes",
    ) as tts_mock:
        result = generate_interview_start_tts(
            is_first_finished=True,
            db_question="Saved DB question",
            voice="Jessica",
        )

    assert result == {
        "question_text": "Saved DB question",
        "tts_audio_base64": base64.b64encode(b"tts-bytes").decode("ascii"),
        "tts_audio_mime_type": "audio/wav",
    }
    tts_mock.assert_called_once_with("Saved DB question", voice="Jessica")
