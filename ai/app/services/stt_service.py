import logging
import time

from app.models.stt.openai_stt import transcribe_audio_file

logger = logging.getLogger(__name__)


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
