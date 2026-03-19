from __future__ import annotations

import base64
import shutil
import tempfile
from pathlib import Path

from fastapi import UploadFile

from app.pipeline.interview_pipeline import run_interview_pipeline
from app.prompts.interview_prompts import NEXT_OPENING_SYSTEM_PROMPT, TITLE_GENERATION_SYSTEM_PROMPT
from app.services.llm_service import create_message
from app.services.tts_service import text_to_speech

# 현재 인터뷰 답변 업로드 API는 webm 파일만 받도록 제한한다.
ALLOWED_AUDIO_EXTENSIONS = {".webm"}
# JSON 응답에서 사용하는 기본 오디오 MIME 타입이다.
DEFAULT_AUDIO_MIME_TYPE = "audio/wav"
# 신규 유저의 첫 인터뷰 시작에서만 사용하는 하드코딩 질문 템플릿이다.
NEW_USER_START_QUESTION_TEMPLATE = (
    "안녕하세요. {nickname}님. 오늘 시간 내주셔서 감사해요. 요즘 어떻게 지내고 계시나요?"
)


def validate_audio_upload(audio_file: UploadFile) -> None:
    """업로드된 음성 파일이 현재 지원 포맷인지 검증한다.

    Args:
        audio_file: FastAPI multipart/form-data로 전달된 업로드 파일.

    Raises:
        ValueError: 지원하지 않는 확장자면 요청을 거절한다.
    """

    suffix = Path(audio_file.filename or "").suffix.lower()
    if suffix not in ALLOWED_AUDIO_EXTENSIONS:
        raise ValueError("Only .webm audio uploads are supported.")


def build_interview_start_question_text(
    is_first_finished: bool,
    nickname: str | None = None,
    db_question: str | None = None,
) -> str:
    """인터뷰 시작 시점에 TTS로 읽을 최종 질문 텍스트를 결정한다.

    첫 인터뷰를 아직 마치지 않은 사용자는 하드코딩 템플릿에 닉네임을 치환하고,
    첫 인터뷰를 이미 마친 사용자는 DB 질문을 그대로 사용한다.
    API 계층에서는 이 함수가 발생시키는 `ValueError`를 400 응답으로 변환한다.

    Args:
        is_first_finished: 백엔드가 전달한 첫 인터뷰 완료 여부.
        nickname: 첫 인터뷰 시작 사용자일 때 템플릿에 삽입할 닉네임.
        db_question: 첫 인터뷰를 이미 마친 사용자일 때 사용할 DB 질문 텍스트.

    Returns:
        str: TTS provider에 실제로 전달할 최종 질문 텍스트.

    Raises:
        ValueError: 분기별 필수 값이 비어 있으면 예외를 발생시킨다.
    """

    if not is_first_finished:
        normalized_nickname = (nickname or "").strip()
        if not normalized_nickname:
            raise ValueError("nickname is required when is_first_finished is false.")

        return NEW_USER_START_QUESTION_TEMPLATE.format(nickname=normalized_nickname)

    normalized_db_question = (db_question or "").strip()
    if not normalized_db_question:
        raise ValueError("db_question is required when is_first_finished is true.")

    return normalized_db_question


def generate_interview_start_tts(
    is_first_finished: bool,
    nickname: str | None = None,
    db_question: str | None = None,
) -> dict[str, str]:
    """인터뷰 시작 질문을 결정하고 TTS 오디오까지 생성해 API 응답 형태로 반환한다.

    Args:
        is_first_finished: 첫 인터뷰 완료 여부.
        nickname: 첫 인터뷰 시작 사용자일 때 하드코딩 질문에 삽입할 닉네임.
        db_question: 첫 인터뷰를 이미 마친 사용자일 때 사용할 DB 질문.

    Returns:
        dict[str, str]: 최종 질문 텍스트와 base64 인코딩된 TTS 오디오 응답 데이터.
    """

    question_text = build_interview_start_question_text(
        is_first_finished=is_first_finished,
        nickname=nickname,
        db_question=db_question,
    )
    tts_audio = text_to_speech(question_text)

    return {
        "question_text": question_text,
        "tts_audio_base64": base64.b64encode(tts_audio).decode("ascii"),
        "tts_audio_mime_type": DEFAULT_AUDIO_MIME_TYPE,
    }


def generate_next_opening_question(
    interview_context: str,
    nickname: str | None = None,
    max_tokens: int = 256,
) -> dict[str, str]:
    """이전 인터뷰 내용을 바탕으로 다음 회차의 첫 질문을 생성한다.

    어떤 인터뷰 내용을 `interview_context`에 담을지는 백엔드가 판단한다.
    (2회차: 1회차 5번째 Q&A, 3회차+: 직전 인터뷰 전체 Q&A)
    생성된 질문은 백엔드가 DB에 저장한 뒤 /start-tts의 db_question으로 사용한다.

    Args:
        interview_context: 백엔드가 조합한 직전 인터뷰 내용 텍스트.
        nickname: 환영 인사에 사용할 사용자 닉네임.
        max_tokens: LLM 최대 토큰 수.

    Returns:
        dict[str, str]: 생성된 다음 회차 첫 질문 텍스트.
    """

    nickname_line = f"사용자 닉네임: {nickname}\n\n" if (nickname or "").strip() else ""
    user_message = f"{nickname_line}이전 인터뷰 내용:\n{interview_context}"

    db_question = create_message(
        messages=[{"role": "user", "content": user_message}],
        system=NEXT_OPENING_SYSTEM_PROMPT,
        max_tokens=max_tokens,
    )

    return {"db_question": db_question}


def generate_magazine_title(
    interview_context: str,
    max_tokens: int = 64,
) -> dict[str, str]:
    """인터뷰 전체 내용을 바탕으로 매거진 스타일 타이틀을 생성한다.

    생성된 타이틀은 백엔드가 매거진 아티클에 사용한다.

    Args:
        interview_context: 백엔드가 조합한 인터뷰 전체 Q&A 텍스트.
        max_tokens: LLM 최대 토큰 수.

    Returns:
        dict[str, str]: 생성된 매거진 타이틀 텍스트.
    """

    title = create_message(
        messages=[{"role": "user", "content": interview_context}],
        system=TITLE_GENERATION_SYSTEM_PROMPT,
        max_tokens=max_tokens,
    )

    return {"title": title}


def run_interview_pipeline_from_upload(
    audio_file: UploadFile,
    is_first_finished: bool = False,
    sequence: int | None = None,
    nickname: str | None = None,
    previous_question: str | None = None,
    max_tokens: int = 1024,
) -> dict[str, str]:
    """업로드 음성 파일을 인터뷰 파이프라인에 전달하고 API 응답 형태로 변환한다.

    Args:
        audio_file: 인터뷰 답변 음성 파일.
        is_first_finished: 첫 질문 응답이 끝난 뒤인지 여부.
        sequence: 첫 인터뷰 질문 순서.
        nickname: 첫 인터뷰 질문 생성에 사용할 닉네임.
        previous_question: 이전 질문 텍스트.
        max_tokens: LLM 최대 토큰 수.

    Returns:
        dict[str, str]: STT 결과, 후속 질문, base64 인코딩된 TTS 오디오를 담은 응답 데이터.

    Raises:
        ValueError: 지원하지 않는 업로드 파일 형식이면 예외를 발생시킨다.
    """

    validate_audio_upload(audio_file)

    suffix = Path(audio_file.filename or "audio.webm").suffix.lower() or ".webm"
    temp_file_path: Path | None = None

    try:
        # STT provider는 파일 경로 기반 입력을 받기 때문에 업로드 스트림을 임시 파일로 복사한다.
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            shutil.copyfileobj(audio_file.file, temp_file)
            temp_file_path = Path(temp_file.name)

        pipeline_result = run_interview_pipeline(
            str(temp_file_path),
            is_first_finished=is_first_finished,
            sequence=sequence,
            nickname=nickname,
            previous_question=previous_question,
            max_tokens=max_tokens,
        )
        return {
            "stt_text": pipeline_result.stt_text,
            "question_text": pipeline_result.llm_text,
            "tts_audio_base64": base64.b64encode(pipeline_result.tts_audio).decode("ascii"),
            "tts_audio_mime_type": DEFAULT_AUDIO_MIME_TYPE,
        }
    finally:
        audio_file.file.close()
        if temp_file_path is not None and temp_file_path.exists():
            temp_file_path.unlink()
