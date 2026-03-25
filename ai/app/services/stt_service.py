import logging
import re
import shutil
import tempfile
import time
from dataclasses import dataclass
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
SUSPICIOUS_STT_PHRASES = (
    "mbc",
    "시청해주셔서 감사합니다",
    "구독과 좋아요",
    "다음 영상에서 만나요",
)


@dataclass(frozen=True)
class NoSpeechAnalysis:
    peak_rms: float
    dynamic_threshold: float
    voiced_ms: int
    duration_ms: int
    voiced_ratio: float
    is_no_speech: bool


def validate_audio_upload(audio_file: UploadFile) -> None:
    suffix = Path(audio_file.filename or "").suffix.lower()
    if suffix not in ALLOWED_AUDIO_EXTENSIONS:
        raise ValueError("Only .webm audio uploads are supported.")


def normalize_stt_text(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def collapse_stt_text(text: str) -> str:
    return re.sub(r"[\s\W_]+", "", text.lower())


def is_suspicious_silence_hallucination(text: str) -> bool:
    collapsed = collapse_stt_text(normalize_stt_text(text))
    if not collapsed:
        return False
    return any(collapse_stt_text(phrase) in collapsed for phrase in SUSPICIOUS_STT_PHRASES)


def _looks_like_low_confidence_non_speech(analysis: NoSpeechAnalysis) -> bool:
    low_peak = analysis.peak_rms < max(STT_NO_SPEECH_PEAK_THRESHOLD * 15, 0.3)
    short_voiced = analysis.voiced_ms < STT_NO_SPEECH_MIN_VOICED_MS * 5
    sparse_voice = analysis.voiced_ratio < 0.12
    return low_peak and (short_voiced or sparse_voice)


def sanitize_stt_text(text: str, analysis: NoSpeechAnalysis | None = None) -> str:
    normalized = normalize_stt_text(text)
    if not re.search(r"[A-Za-z0-9가-힣]", normalized):
        logger.warning("[STT] filtered non-speech placeholder text=%s", normalized)
        return ""
    if (
        analysis is not None
        and is_suspicious_silence_hallucination(normalized)
        and _looks_like_low_confidence_non_speech(analysis)
    ):
        logger.warning("[STT] filtered suspicious silence hallucination text=%s", normalized)
        return ""
    return normalized


def _audiosegment_to_mono_float32(audio: AudioSegment) -> tuple[np.ndarray, int]:
    normalized = audio.set_channels(1)
    sample_rate = normalized.frame_rate
    samples = np.array(normalized.get_array_of_samples(), dtype=np.float32)
    scale = float(1 << (8 * normalized.sample_width - 1))
    if scale <= 0:
        return samples, sample_rate
    return samples / scale, sample_rate


def analyze_no_speech_audio_file(audio_file_path: str) -> NoSpeechAnalysis | None:
    try:
        audio = AudioSegment.from_file(audio_file_path)
        samples, sample_rate = _audiosegment_to_mono_float32(audio)
    except Exception as exc:
        logger.warning("[STT] no-speech check skipped path=%s error=%s", audio_file_path, exc)
        return None

    if sample_rate <= 0 or samples.size == 0:
        logger.info("[STT] no-speech detected path=%s reason=empty_audio", audio_file_path)
        return NoSpeechAnalysis(
            peak_rms=0.0,
            dynamic_threshold=STT_NO_SPEECH_RMS_THRESHOLD,
            voiced_ms=0,
            duration_ms=0,
            voiced_ratio=0.0,
            is_no_speech=True,
        )

    frame_length = max(1, int(sample_rate * STT_NO_SPEECH_FRAME_MS / 1000))
    hop_length = max(1, int(sample_rate * STT_NO_SPEECH_HOP_MS / 1000))
    if samples.size < frame_length:
        samples = np.pad(samples, (0, frame_length - samples.size))

    frames = np.lib.stride_tricks.sliding_window_view(samples, frame_length)[::hop_length]
    if frames.size == 0:
        logger.info("[STT] no-speech detected path=%s reason=no_frames", audio_file_path)
        return NoSpeechAnalysis(
            peak_rms=0.0,
            dynamic_threshold=STT_NO_SPEECH_RMS_THRESHOLD,
            voiced_ms=0,
            duration_ms=int(samples.size * 1000 / sample_rate),
            voiced_ratio=0.0,
            is_no_speech=True,
        )

    rms = np.sqrt(np.mean(np.square(frames), axis=1) + 1e-12)
    peak_rms = float(np.max(rms))
    dynamic_threshold = max(STT_NO_SPEECH_RMS_THRESHOLD, peak_rms * 0.35)
    voiced_frames = int(np.sum(rms >= dynamic_threshold))
    voiced_ms = voiced_frames * STT_NO_SPEECH_HOP_MS
    duration_ms = int(samples.size * 1000 / sample_rate)
    voiced_ratio = voiced_ms / duration_ms if duration_ms > 0 else 0.0
    is_no_speech = (
        peak_rms < STT_NO_SPEECH_PEAK_THRESHOLD or voiced_ms < STT_NO_SPEECH_MIN_VOICED_MS
    )

    logger.info(
        (
            "[STT] no-speech check path=%s peak_rms=%.6f threshold=%.6f "
            "voiced_ms=%s duration_ms=%s voiced_ratio=%.3f min_voiced_ms=%s is_no_speech=%s"
        ),
        audio_file_path,
        peak_rms,
        dynamic_threshold,
        voiced_ms,
        duration_ms,
        voiced_ratio,
        STT_NO_SPEECH_MIN_VOICED_MS,
        is_no_speech,
    )
    return NoSpeechAnalysis(
        peak_rms=peak_rms,
        dynamic_threshold=dynamic_threshold,
        voiced_ms=voiced_ms,
        duration_ms=duration_ms,
        voiced_ratio=voiced_ratio,
        is_no_speech=is_no_speech,
    )


def is_no_speech_audio_file(audio_file_path: str) -> bool:
    analysis = analyze_no_speech_audio_file(audio_file_path)
    if analysis is None:
        return False
    return analysis.is_no_speech


def speech_to_text(audio_file_path: str) -> str:
    started = time.perf_counter()
    analysis = analyze_no_speech_audio_file(audio_file_path)
    if analysis is not None and analysis.is_no_speech:
        elapsed = time.perf_counter() - started
        logger.info("[STT] skipped in %.3fs path=%s reason=no_speech", elapsed, audio_file_path)
        return ""

    raw_stt_text = transcribe_audio_file(audio_file_path)
    stt_text = sanitize_stt_text(raw_stt_text, analysis=analysis)
    elapsed = time.perf_counter() - started

    logger.info(
        "[STT] completed in %.3fs path=%s raw_text=%s sanitized_text=%s",
        elapsed,
        audio_file_path,
        raw_stt_text,
        stt_text,
    )
    return stt_text


def speech_to_text_from_upload(audio_file: UploadFile) -> dict[str, str]:
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
