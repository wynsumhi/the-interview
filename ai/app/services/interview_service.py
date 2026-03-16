from __future__ import annotations

import base64
import shutil
import tempfile
from pathlib import Path

from fastapi import UploadFile

from app.pipeline.interview_pipeline import run_interview_pipeline

# 현재 인터뷰 업로드 API는 백엔드 연동 규격상 `webm` 오디오만 허용한다.
ALLOWED_AUDIO_EXTENSIONS = {".webm"}
# 현재 TTS 구현이 mp3 계열 바이트를 반환하므로 응답 MIME 타입을 고정해 내려준다.
DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg"


def validate_audio_upload(audio_file: UploadFile) -> None:
    """업로드된 음성 파일이 현재 인터뷰 API 규격에 맞는지 검증한다.

    Args:
        audio_file: FastAPI가 multipart/form-data 요청에서 파싱한 업로드 파일 객체.

    Raises:
        ValueError: 파일 확장자가 `.webm`이 아니어서 현재 인터뷰 API 규격에 맞지 않을 때 발생한다.
    """

    suffix = Path(audio_file.filename or "").suffix.lower()
    if suffix not in ALLOWED_AUDIO_EXTENSIONS:
        raise ValueError("Only .webm audio uploads are supported.")


def run_interview_pipeline_from_upload(
    audio_file: UploadFile,
    is_first_finished: bool = True,
    sequence: int | None = None,
    nickname: str | None = None,
    previous_question: str | None = None,
    max_tokens: int = 1024,
) -> dict[str, str]:
    """업로드된 음성 파일로 인터뷰 파이프라인을 실행하고 API 응답 형태로 변환한다.

    최초 인터뷰와 일반 인터뷰 모두 이 함수에서 처리한다.
    분기 로직은 파이프라인 내부에 위임하고, 이 함수는 파일 처리와 응답 직렬화만 담당한다.

    Args:
        audio_file: 백엔드가 전달한 면접 답변 음성 파일. 현재는 `.webm`만 허용한다.
        is_first_finished: 최초 인터뷰 완료 여부. False이면 최초 인터뷰 프롬프트를 사용한다.
        sequence: 최초 인터뷰 질문 순서 (1~5). is_first_finished=False일 때만 사용한다.
        nickname: 사용자 닉네임. is_first_finished=False일 때 LLM 호칭으로 활용한다.
        previous_question: 직전 AI 질문 텍스트. 최초 인터뷰이고 sequence >= 2일 때 사용한다.
        max_tokens: LLM이 생성할 질문의 최대 토큰 수.

    Returns:
        dict[str, str]: API 응답으로 바로 사용할 수 있는 사전.
            `stt_text`, `question_text`, `tts_audio_base64`, `tts_audio_mime_type` 키를 포함한다.

    Raises:
        ValueError: 업로드 파일이 허용된 오디오 형식이 아닐 때 발생한다.
    """

    validate_audio_upload(audio_file)

    suffix = Path(audio_file.filename or "audio.webm").suffix.lower() or ".webm"
    temp_file_path: Path | None = None

    try:
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
