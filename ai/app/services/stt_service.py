import logging
import re
import shutil
import tempfile
import time
from pathlib import Path

import numpy as np
from fastapi import UploadFile
from pydub import AudioSegment

from app.core.settings import (
    STT_NO_SPEECH_FRAME_MS,
    STT_NO_SPEECH_HOP_MS,
    STT_NO_SPEECH_MIN_VOICED_MS,
    STT_NO_SPEECH_PEAK_THRESHOLD,
    STT_NO_SPEECH_RMS_THRESHOLD,
)
from app.models.stt.openai_stt import transcribe_audio_file

logger = logging.getLogger("uvicorn.error")
ALLOWED_AUDIO_EXTENSIONS = {".webm"}
# 무음 hallucination에서 반복적으로 관찰된 대표 문구들이다.
# 완전한 일반화 규칙은 아니므로,
# "실제 면접 답변으로 나올 가능성은 낮고 무음 hallucination으로는 자주 보이는"
# 문구만 최소 집합으로 유지한다.
SUSPICIOUS_STT_PHRASES = (
    "mbc뉴스",
    "시청해주셔서 감사합니다",
    "뉴스였습니다",
    "이덕영입니다",
)


def validate_audio_upload(audio_file: UploadFile) -> None:
    """Reject unsupported upload extensions before invoking the STT provider."""

    suffix = Path(audio_file.filename or "").suffix.lower()
    if suffix not in ALLOWED_AUDIO_EXTENSIONS:
        raise ValueError("Only .webm audio uploads are supported.")


def normalize_stt_text(text: str) -> str:
    # 비교 전에 공백 변형을 먼저 정리해 두면 후속 필터가 문장부호/줄바꿈 차이에 덜 흔들린다.
    return re.sub(r"\s+", " ", text).strip()


def collapse_stt_text(text: str) -> str:
    # hallucination 문구 필터는 공백/문장부호 차이만 있는 변형을 같은 문자열로 취급해야 한다.
    return re.sub(r"[\s\W_]+", "", text.lower())


def is_suspicious_silence_hallucination(text: str) -> bool:
    # STT 결과를 강하게 정규화한 뒤, 알려진 무음 hallucination 패턴 포함 여부만 확인한다.
    collapsed = collapse_stt_text(normalize_stt_text(text))
    if not collapsed:
        return False
    return any(collapse_stt_text(phrase) in collapsed for phrase in SUSPICIOUS_STT_PHRASES)


def sanitize_stt_text(text: str) -> str:
    # 최종 STT 결과를 사용자/후속 파이프라인에 넘기기 전에 최소한의 방어 정리를 수행한다.
    # 1) 의미 있는 문자 자체가 없는 placeholder 출력 제거
    # 2) 방송 클로징 멘트처럼 알려진 무음 hallucination 제거
    normalized = normalize_stt_text(text)
    if not re.search(r"[A-Za-z0-9가-힣]", normalized):
        logger.warning("[STT] filtered non-speech placeholder text=%s", normalized)
        return ""
    if is_suspicious_silence_hallucination(normalized):
        logger.warning("[STT] filtered suspicious silence hallucination text=%s", normalized)
        return ""
    return normalized


def _audiosegment_to_mono_float32(audio: AudioSegment) -> tuple[np.ndarray, int]:
    # pydub가 들고 있는 정수 PCM 샘플을 1채널 float32(-1.0~1.0 근처)로 바꾼다.
    # no-speech 판정은 채널 수/비트폭과 무관하게 같은 수식으로 계산해야 하므로
    # 분석 전에 분석 친화적인 공통 형식으로 맞춰 둔다.
    normalized = audio.set_channels(1)
    sample_rate = normalized.frame_rate
    samples = np.array(normalized.get_array_of_samples(), dtype=np.float32)
    scale = float(1 << (8 * normalized.sample_width - 1))
    if scale <= 0:
        return samples, sample_rate
    return samples / scale, sample_rate


def is_no_speech_audio_file(audio_file_path: str) -> bool:
    # 목적은 "정교한 VAD"가 아니라 "거의 확실한 무음을 STT에 보내지 않는 것"이다.
    # 디코딩 실패 시에는 보수적으로 False를 반환해 기존 STT 흐름을 유지한다.
    try:
        audio = AudioSegment.from_file(audio_file_path)
        samples, sample_rate = _audiosegment_to_mono_float32(audio)
    except Exception as exc:
        logger.warning("[STT] no-speech check skipped path=%s error=%s", audio_file_path, exc)
        return False

    if sample_rate <= 0 or samples.size == 0:
        logger.info("[STT] no-speech detected path=%s reason=empty_audio", audio_file_path)
        return True

    frame_length = max(1, int(sample_rate * STT_NO_SPEECH_FRAME_MS / 1000))
    hop_length = max(1, int(sample_rate * STT_NO_SPEECH_HOP_MS / 1000))
    if samples.size < frame_length:
        # 매우 짧은 파일도 최소 한 프레임은 분석할 수 있도록 0-padding 한다.
        samples = np.pad(samples, (0, frame_length - samples.size))

    frames = np.lib.stride_tricks.sliding_window_view(samples, frame_length)[::hop_length]
    if frames.size == 0:
        logger.info("[STT] no-speech detected path=%s reason=no_frames", audio_file_path)
        return True

    rms = np.sqrt(np.mean(np.square(frames), axis=1) + 1e-12)
    peak_rms = float(np.max(rms))
    # 절대 임계값만 쓰면 마이크 게인 차이에 취약하고,
    # 상대 임계값만 쓰면 거의 무음인데도 peak 하나 때문에 통과할 수 있다.
    # 그래서 절대 하한선과 peak 기반 상대 하한선 중 큰 값을 사용한다.
    dynamic_threshold = max(STT_NO_SPEECH_RMS_THRESHOLD, peak_rms * 0.35)
    voiced_frames = int(np.sum(rms >= dynamic_threshold))
    voiced_ms = voiced_frames * STT_NO_SPEECH_HOP_MS
    # 아래 조건은 OR로 묶는다.
    # - peak 자체가 너무 작으면 전체가 사실상 무음
    # - peak는 있었어도 유효 길이가 너무 짧으면 클릭/잡음/짧은 아티팩트일 가능성이 크다
    is_no_speech = (
        peak_rms < STT_NO_SPEECH_PEAK_THRESHOLD or voiced_ms < STT_NO_SPEECH_MIN_VOICED_MS
    )

    logger.info(
        (
            "[STT] no-speech check path=%s peak_rms=%.6f threshold=%.6f "
            "voiced_ms=%s min_voiced_ms=%s is_no_speech=%s"
        ),
        audio_file_path,
        peak_rms,
        dynamic_threshold,
        voiced_ms,
        STT_NO_SPEECH_MIN_VOICED_MS,
        is_no_speech,
    )
    return is_no_speech


#
# 서버 측 STT 진입점이다.
# 처리 순서는 다음과 같다.
# 1. 파일이 사실상 무음인지 먼저 판정한다.
# 2. 무음이면 외부 STT 호출을 생략하고 빈 문자열을 반환한다.
# 3. 무음이 아니면 STT를 호출한다.
# 4. 결과 텍스트를 후처리해 알려진 hallucination을 제거한다.
#
def speech_to_text(audio_file_path: str) -> str:
    """STT 호출 시간과 전사 결과를 서비스 로그로 남긴다.

    운영 중 음성 인식 단계의 지연과 실제 변환 결과를 함께 확인할 수 있도록
    모델 응답 시간과 최종 텍스트를 한 줄 로그로 기록한다.
    """

    started = time.perf_counter()
    if is_no_speech_audio_file(audio_file_path):
        elapsed = time.perf_counter() - started
        logger.info("[STT] skipped in %.3fs path=%s reason=no_speech", elapsed, audio_file_path)
        return ""

    raw_stt_text = transcribe_audio_file(audio_file_path)
    stt_text = sanitize_stt_text(raw_stt_text)
    elapsed = time.perf_counter() - started

    logger.info(
        "[STT] completed in %.3fs path=%s raw_text=%s sanitized_text=%s",
        elapsed,
        audio_file_path,
        raw_stt_text,
        stt_text,
    )
    return stt_text


# FastAPI UploadFile은 스트림 기반이므로, 현재처럼 파일 경로를 받는 서비스와 연결하려면
# 먼저 임시 파일로 내려야 한다. 처리 후에는 임시 파일을 반드시 삭제한다.
def speech_to_text_from_upload(audio_file: UploadFile) -> dict[str, str]:
    """Persist an uploaded file temporarily and return its transcription."""

    validate_audio_upload(audio_file)

    suffix = Path(audio_file.filename or "audio.webm").suffix.lower() or ".webm"
    temp_file_path: Path | None = None

    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
            # UploadFile.file은 파일 객체이므로 전체를 임시 파일로 복사한다.
            shutil.copyfileobj(audio_file.file, temp_file)
            temp_file_path = Path(temp_file.name)

        return {"stt_text": speech_to_text(str(temp_file_path))}
    finally:
        # 업로드 스트림과 임시 파일을 항상 정리한다.
        audio_file.file.close()
        if temp_file_path is not None and temp_file_path.exists():
            temp_file_path.unlink()
