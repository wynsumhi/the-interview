import logging
import time

from app.models.tts.supertonic_tts import synthesize_speech
from app.models.tts.voice_catalog import resolve_supertonic_voice_id

logger = logging.getLogger("uvicorn.error")


def text_to_speech(question_text: str, voice: str | None = None) -> bytes:
    """TTS 호출 시간과 변환 완료 정보를 서비스 로그로 남긴다.

    음성 바이트 자체는 로그에 남기지 않고, 처리 시간과 입력 길이, 출력 크기만 기록해
    운영 환경에서 TTS 성능 변화를 빠르게 확인할 수 있게 한다.
    """

    started = time.perf_counter()
    resolved_voice = resolve_supertonic_voice_id(voice)
    audio_bytes = synthesize_speech(question_text, voice=resolved_voice)
    elapsed = time.perf_counter() - started

    logger.info(
        "[TTS] completed in %.3fs voice=%s chars=%s bytes=%s",
        elapsed,
        resolved_voice,
        len(question_text),
        len(audio_bytes),
    )
    return audio_bytes
