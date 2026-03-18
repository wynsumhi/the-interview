import logging
import time

from app.models.tts.supertonic_tts import synthesize_speech

logger = logging.getLogger(__name__)


def text_to_speech(question_text: str) -> bytes:
    """TTS 호출 시간과 변환 완료 정보를 서비스 로그로 남긴다.

    음성 바이트 자체는 로그에 남기지 않고, 처리 시간과 입력 길이, 출력 크기만 기록해
    운영 환경에서 TTS 성능 변화를 빠르게 확인할 수 있게 한다.
    """

    started = time.perf_counter()
    audio_bytes = synthesize_speech(question_text)
    elapsed = time.perf_counter() - started

    logger.info(
        "TTS completed in %.3fs. chars=%s bytes=%s",
        elapsed,
        len(question_text),
        len(audio_bytes),
    )
    return audio_bytes
