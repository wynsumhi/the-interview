from __future__ import annotations

import base64
import shutil
import tempfile
from pathlib import Path
from typing import Any

from fastapi import UploadFile

from app.pipeline.interview_pipeline import (
    run_interview_pipeline,
    run_interview_pipeline_from_stt_text,
)
from app.prompts.interview_prompts import (
    KEYWORD_GENERATION_SYSTEM_PROMPT,
    NEXT_OPENING_SYSTEM_PROMPT,
    TITLE_GENERATION_SYSTEM_PROMPT,
)
from app.services.llm_service import create_message
from app.services.tts_service import text_to_speech

# 현재 업로드 기반 인터뷰 API는 webm만 허용한다.
# STT provider와 실제 프론트 입력 포맷을 맞춘 최소 계약이다.
ALLOWED_AUDIO_EXTENSIONS = {".webm"}

# JSON 응답에서 TTS 오디오 타입을 명시할 때 사용하는 기본 MIME 타입이다.
DEFAULT_AUDIO_MIME_TYPE = "audio/wav"
TEMP_INTERVIEW_STATE_ANSWER_ID = 0

# 신규 사용자의 첫 인터뷰 시작 시 사용하는 기본 질문 템플릿이다.
# 이후 백엔드에서 별도 질문을 넘겨주기 전까지는 이 문장을 TTS로 읽는다.
NEW_USER_START_QUESTION_TEMPLATE = (
    "안녕하세요 {nickname}님, 오늘 시간 내주셔서 감사합니다. 요즘 어떻게 지내시고 계세요?"
)


def validate_audio_upload(audio_file: UploadFile) -> None:
    """업로드된 인터뷰 음성 파일 확장자가 현재 지원 대상인지 검증한다.

    API 진입점에서 잘못된 포맷을 먼저 걸러야
    STT provider 호출 전에 빠르게 400 응답으로 반환할 수 있다.
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

    첫 인터뷰가 아직 끝나지 않은 사용자는 기본 템플릿을 사용하고,
    이미 이전 인터뷰가 있는 사용자는 백엔드가 넘긴 질문을 그대로 사용한다.
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
    """인터뷰 시작 질문과 그에 대한 TTS 오디오를 함께 생성한다.

    반환값은 바로 API 응답에 실을 수 있도록
    텍스트와 base64 인코딩된 오디오를 포함한 dict 형태를 사용한다.
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
    """이전 인터뷰 맥락을 바탕으로 다음 회차의 첫 질문을 생성한다.

    이 함수는 현재 인터뷰 파이프라인과는 별개로,
    회차 간 연결 질문을 생성하는 보조 서비스 역할을 맡는다.
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
    """인터뷰 전체 맥락을 바탕으로 매거진 제목을 생성한다."""

    title = create_message(
        messages=[{"role": "user", "content": interview_context}],
        system=TITLE_GENERATION_SYSTEM_PROMPT,
        max_tokens=max_tokens,
    )
    return {"title": title}


def generate_keyword(
    question: str,
    answer: str,
    max_tokens: int = 32,
) -> dict[str, str]:
    """질문-답변 쌍을 바탕으로 북마크 키워드를 생성한다."""

    keyword = create_message(
        messages=[{"role": "user", "content": f"Q: {question}\nA: {answer}"}],
        system=KEYWORD_GENERATION_SYSTEM_PROMPT,
        max_tokens=max_tokens,
    )
    return {"keyword": keyword}


def save_interview_state_temp(request: dict[str, Any]) -> dict[str, Any]:
    """임시 인터뷰 상태 저장 요청을 그대로 감싸서 반환한다.

    실제 백엔드 저장 API가 아직 준비되지 않았기 때문에,
    우선 AI 서버가 어떤 형태의 payload를 보낼지 계약을 고정하는 용도로 둔다.
    """

    return {
        "status": "queued",
        "message": "Temporary interview-state save request accepted.",
        "request": request,
    }


def run_interview_pipeline_from_upload(
    audio_file: UploadFile,
    answer_id: int,
    is_first_finished: bool = False,
    user_id: str | None = None,
    sequence: int | None = None,
    nickname: str | None = None,
    previous_question: str | None = None,
    max_tokens: int = 1024,
) -> dict[str, Any]:
    """업로드된 답변 음성을 인터뷰 파이프라인에 전달하고 API 응답 형태로 변환한다.

    처리 순서는 다음과 같다.
    1. 업로드 포맷 검증
    2. 임시 파일 저장
    3. 파이프라인 실행
    4. 응답 직렬화
    5. 임시 파일 정리
    """

    validate_audio_upload(audio_file)

    suffix = Path(audio_file.filename or "audio.webm").suffix.lower() or ".webm"
    temp_file_path: Path | None = None

    try:
        # STT provider는 파일 경로 기반 입력을 기대하므로,
        # multipart 업로드 스트림을 그대로 넘기지 않고 임시 파일로 복사한다.
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            shutil.copyfileobj(audio_file.file, temp_file)
            temp_file_path = Path(temp_file.name)

        pipeline_result = run_interview_pipeline(
            str(temp_file_path),
            answer_id=answer_id,
            is_first_finished=is_first_finished,
            user_id=user_id,
            sequence=sequence,
            nickname=nickname,
            previous_question=previous_question,
            max_tokens=max_tokens,
        )

        # 파이프라인 내부 결과는 bytes와 dataclass를 함께 들고 있으므로,
        # API 응답 직전에는 JSON 직렬화 가능한 dict 형태로 한 번 정리한다.
        response = pipeline_result.to_response_dict()
        return {
            "stt_text": response["stt_text"],
            "interview_state_db": response["interview_state_db"],
            "interview_state_prompt": response["interview_state_prompt"],
            "question_text": response["question_text"],
            "tts_audio_base64": base64.b64encode(response["tts_audio"]).decode("ascii"),
            "tts_audio_mime_type": DEFAULT_AUDIO_MIME_TYPE,
        }
    finally:
        audio_file.file.close()
        if temp_file_path is not None and temp_file_path.exists():
            temp_file_path.unlink()


def run_interview_pipeline_from_stt_payload(
    audio_file: UploadFile,
    stt_text: str,
    is_first_finished: bool = False,
    user_id: str | None = None,
    sequence: int | None = None,
    nickname: str | None = None,
    previous_question: str | None = None,
    max_tokens: int = 1024,
) -> dict[str, Any]:
    """STT 텍스트를 외부에서 받아 STT 이후 파이프라인만 실행한다."""

    validate_audio_upload(audio_file)

    normalized_stt_text = stt_text.strip()
    if not normalized_stt_text:
        raise ValueError("stt_text is required.")

    suffix = Path(audio_file.filename or "audio.webm").suffix.lower() or ".webm"
    temp_file_path: Path | None = None

    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            shutil.copyfileobj(audio_file.file, temp_file)
            temp_file_path = Path(temp_file.name)

        pipeline_result = run_interview_pipeline_from_stt_text(
            str(temp_file_path),
            stt_text=normalized_stt_text,
            answer_id=TEMP_INTERVIEW_STATE_ANSWER_ID,
            is_first_finished=is_first_finished,
            user_id=user_id,
            sequence=sequence,
            nickname=nickname,
            previous_question=previous_question,
            max_tokens=max_tokens,
        )

        response = pipeline_result.to_response_dict()
        return {
            "stt_text": response["stt_text"],
            "interview_state_db": response["interview_state_db"],
            "interview_state_prompt": response["interview_state_prompt"],
            "question_text": response["question_text"],
            "tts_audio_base64": base64.b64encode(response["tts_audio"]).decode("ascii"),
            "tts_audio_mime_type": DEFAULT_AUDIO_MIME_TYPE,
        }
    finally:
        audio_file.file.close()
        if temp_file_path is not None and temp_file_path.exists():
            temp_file_path.unlink()
