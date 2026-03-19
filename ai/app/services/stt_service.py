import logging
import shutil
import tempfile
import time
from pathlib import Path

from fastapi import UploadFile

from app.models.stt.openai_stt import transcribe_audio_file

logger = logging.getLogger("uvicorn.error")
ALLOWED_AUDIO_EXTENSIONS = {".webm"}


def validate_audio_upload(audio_file: UploadFile) -> None:
    """Reject unsupported upload extensions before invoking the STT provider."""

    suffix = Path(audio_file.filename or "").suffix.lower()
    if suffix not in ALLOWED_AUDIO_EXTENSIONS:
        raise ValueError("Only .webm audio uploads are supported.")


def speech_to_text(audio_file_path: str) -> str:
    """STT 호출 시간과 전사 결과를 서비스 로그로 남긴다.

    운영 중 음성 인식 단계의 지연과 실제 변환 결과를 함께 확인할 수 있도록
    모델 응답 시간과 최종 텍스트를 한 줄 로그로 기록한다.
    """

    started = time.perf_counter()
    stt_text = transcribe_audio_file(audio_file_path)
    elapsed = time.perf_counter() - started

    logger.info(
        "STT completed in %.3fs. path=%s text=%s",
        elapsed,
        audio_file_path,
        stt_text,
    )
    return stt_text


def speech_to_text_from_upload(audio_file: UploadFile) -> dict[str, str]:
    """Persist an uploaded file temporarily and return its transcription."""

    validate_audio_upload(audio_file)

    suffix = Path(audio_file.filename or "audio.webm").suffix.lower() or ".webm"
    temp_file_path: Path | None = None

    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            shutil.copyfileobj(audio_file.file, temp_file)
            temp_file_path = Path(temp_file.name)

        return {"stt_text": speech_to_text(str(temp_file_path))}
    finally:
        audio_file.file.close()
        if temp_file_path is not None and temp_file_path.exists():
            temp_file_path.unlink()
