from __future__ import annotations

import io
import logging
import time
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import soundfile as sf
from pydub import AudioSegment

logger = logging.getLogger("uvicorn.error")

# 프레임 단위 특징을 계산할 때 사용할 기본 윈도우 길이와 hop 길이다.
# 음성의 에너지, pitch, 침묵 구간 변화를 무리 없이 잡기 위해 짧은 프레임 기반 분석을 사용한다.
FRAME_MS = 25
HOP_MS = 10

# 현재 인터뷰 상태 분석에서 사용하는 정규화된 상태 축이다.
# 이 라벨 집합은 DB 저장용 fused score와 프롬프트 참고용 점수 모두에 공통으로 사용된다.
STATE_ORDER = (
    "calm",
    "tense",
    "anxious",
    "low",
    "engaged",
)

# STT 텍스트에서 직접 탐지할 키워드 집합이다.
# 모델 기반 텍스트 감정 분류가 아니라 설명 가능한 휴리스틱을 쓰기 때문에,
# 왜 특정 상태가 감지됐는지 역추적할 수 있는 키워드 사전을 유지한다.
TEXT_STATE_KEYWORDS = {
    "anxious": (
        "긴장",
        "불안",
        "떨",
        "걱정",
        "신경 쓰",
        "부담",
        "초조",
        "무서",
    ),
    "low": (
        "우울",
        "슬프",
        "의욕",
        "힘들",
        "지치",
        "처지",
        "공허",
        "무기력",
        "속상",
    ),
    "engaged": (
        "좋아하",
        "신나",
        "열정",
        "재미",
        "몰입",
        "흥미",
        "인상적",
        "즐거",
        "반갑",
    ),
    "tense": (
        "빠듯",
        "예민",
        "압박",
        "정신없",
        "쫓기",
    ),
}


@dataclass(frozen=True)
class AudioFeatures:
    """답변 음성에서 추출한 저수준 음성 특징 묶음이다.

    이 값들은 사용자별 보이스 특성 분석이나 답변별 추세 분석에 재사용될 수 있으므로,
    상태 라벨만 따로 저장하는 대신 함께 보존하는 전제를 둔다.
    """

    duration_sec: float
    rms_mean: float
    rms_std: float
    zcr_mean: float
    silence_ratio: float
    voiced_ratio: float
    speech_rate_proxy: float
    pitch_mean_hz: float
    pitch_std_hz: float


@dataclass(frozen=True)
class StateSignal:
    """단일 분석 축의 상태 점수와 대표 상태를 담는 결과 객체다.

    `text_signal`, `voice_signal`, `fused_signal` 모두 같은 구조를 사용해서
    비교와 직렬화를 단순하게 유지한다.
    """

    scores: dict[str, float]
    top_state: str
    top_score: float
    reason: str


@dataclass(frozen=True)
class InterviewStateResult:
    """음성/텍스트/융합 분석 결과를 한 번에 전달하는 내부 결과 객체다.

    이 객체는 파이프라인 내부에서만 사용되는 상세 결과이며,
    이후 DB 저장용 payload와 LLM 참고용 payload로 다시 가공된다.
    """

    transcript: str
    audio_features: AudioFeatures
    text_signal: StateSignal
    voice_signal: StateSignal
    fused_signal: StateSignal


@dataclass(frozen=True)
class InterviewStateDbPayload:
    """백엔드 DB 저장에 바로 사용할 인터뷰 상태 payload다.

    현재 저장 계약은 `answer_id`, `top_state`, `fused_scores`, `audio_features`만 유지한다.
    텍스트/음성 개별 상태는 내부 계산에는 쓰지만 DB 저장 최소 집합에서는 제외한다.
    """

    answer_id: int
    top_state: str
    fused_scores: dict[str, float]
    audio_features: AudioFeatures


@dataclass(frozen=True)
class InterviewStatePromptPayload:
    """LLM이 참고 신호로만 사용할 수 있게 만든 인터뷰 상태 요약 payload다.

    현재 설계에서는 해석 문장을 길게 주입하지 않고,
    `top_state`와 `fused_scores`를 참고용 수치로만 전달한다.
    """

    top_state: str
    fused_scores: dict[str, float]
    formatted_prompt: str


def read_audio(audio_path: Path) -> tuple[np.ndarray, int]:
    """오디오 파일을 읽어 모노 float32 파형으로 정규화한다.

    webm 입력은 `soundfile`이 직접 처리하지 못할 수 있으므로,
    `pydub`으로 메모리 상의 wav 버퍼로 변환한 뒤 읽는다.
    후속 특징 계산은 단일 채널 파형을 가정하므로,
    스테레오 입력은 채널 평균으로 단순화한다.
    """

    audio = AudioSegment.from_file(audio_path)
    wav_io = io.BytesIO()
    audio.export(wav_io, format="wav")
    wav_io.seek(0)

    samples, sample_rate = sf.read(wav_io)
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    return samples.astype(np.float32), sample_rate


def frame_signal(samples: np.ndarray, sample_rate: int) -> np.ndarray:
    """연속 파형을 고정 길이 프레임 배열로 변환한다.

    프레임 기반 분석을 사용해야 RMS, ZCR, pitch 분산처럼
    시간축 변화가 있는 특징을 안정적으로 계산할 수 있다.
    """

    frame_length = max(1, int(sample_rate * FRAME_MS / 1000))
    hop_length = max(1, int(sample_rate * HOP_MS / 1000))
    if len(samples) < frame_length:
        # 너무 짧은 입력도 동일한 계산 경로를 타게 하려고 최소 한 프레임은 강제로 만든다.
        pad = np.zeros(frame_length - len(samples), dtype=np.float32)
        samples = np.concatenate([samples, pad])
    frame_count = 1 + max(0, (len(samples) - frame_length) // hop_length)
    frames = np.lib.stride_tricks.sliding_window_view(samples, frame_length)[::hop_length]
    return frames[:frame_count].copy()


def compute_rms(frames: np.ndarray) -> np.ndarray:
    """프레임별 RMS 에너지를 계산한다."""

    return np.sqrt(np.mean(np.square(frames), axis=1) + 1e-12)


def compute_zcr(frames: np.ndarray) -> np.ndarray:
    """프레임별 zero-crossing rate를 계산한다."""

    signs = np.signbit(frames)
    crossings = np.sum(signs[:, 1:] != signs[:, :-1], axis=1)
    return crossings / np.maximum(1, frames.shape[1] - 1)


def estimate_pitch(frame: np.ndarray, sample_rate: int) -> float:
    """자기상관 기반의 단순 pitch 추정값을 반환한다.

    정확한 음성학 모델이 아니라 휴리스틱 분석용이므로,
    유효한 주기성이 없는 프레임은 0으로 처리한다.
    """

    centered = frame - np.mean(frame)
    energy = np.sum(centered * centered)
    if energy < 1e-6:
        return 0.0

    autocorr = np.correlate(centered, centered, mode="full")[len(centered) - 1 :]
    min_lag = max(1, int(sample_rate / 300))
    max_lag = min(len(autocorr) - 1, int(sample_rate / 70))
    if max_lag <= min_lag:
        return 0.0

    search = autocorr[min_lag:max_lag]
    lag = int(np.argmax(search)) + min_lag
    peak = autocorr[lag]
    if peak <= 0 or peak < 0.3 * autocorr[0]:
        return 0.0
    return float(sample_rate / lag)


def extract_audio_features(audio_path: Path) -> AudioFeatures:
    """음성 파형에서 상태 추정에 필요한 핵심 음성 특징을 추출한다.

    여기서 추출한 값은 현재 상태 판단에도 쓰이고,
    이후 사용자별 평균 보이스 특성 분석에도 재사용할 수 있다.
    """

    samples, sample_rate = read_audio(audio_path)
    frames = frame_signal(samples, sample_rate)
    rms = compute_rms(frames)
    zcr = compute_zcr(frames)

    # 완전 고정 임계치만 사용하면 녹음 볼륨 편차에 취약하므로,
    # 하위 분위수 기반 임계치와 안전 하한을 함께 사용한다.
    silence_threshold = max(0.01, float(np.percentile(rms, 25) * 0.8))
    silence_mask = rms < silence_threshold
    voiced_candidates = frames[rms >= max(silence_threshold, 0.015)]
    pitch_values = np.array([estimate_pitch(frame, sample_rate) for frame in voiced_candidates])
    pitch_values = pitch_values[pitch_values > 0]

    duration_sec = len(samples) / sample_rate if sample_rate > 0 else 0.0
    voiced_ratio = 1.0 - float(np.mean(silence_mask))
    # 정확한 words-per-minute가 아니라,
    # 전체 길이 대비 발화가 얼마나 촘촘하게 채워져 있는지 보는 근사치다.
    speech_rate_proxy = voiced_ratio / duration_sec if duration_sec > 0 else 0.0

    return AudioFeatures(
        duration_sec=round(duration_sec, 6),
        rms_mean=round(float(np.mean(rms)), 6),
        rms_std=round(float(np.std(rms)), 6),
        zcr_mean=round(float(np.mean(zcr)), 6),
        silence_ratio=round(float(np.mean(silence_mask)), 6),
        voiced_ratio=round(voiced_ratio, 6),
        speech_rate_proxy=round(float(speech_rate_proxy), 6),
        pitch_mean_hz=round(float(np.mean(pitch_values)) if pitch_values.size else 0.0, 6),
        pitch_std_hz=round(float(np.std(pitch_values)) if pitch_values.size else 0.0, 6),
    )


def _normalize_scores(raw_scores: dict[str, float]) -> dict[str, float]:
    """상태 점수를 0 이상으로 보정한 뒤 합이 1이 되도록 정규화한다."""

    bounded = {state: max(0.0, float(score)) for state, score in raw_scores.items()}
    total = sum(bounded.values())
    if total <= 0:
        return {"calm": 1.0}
    return {
        state: round(score / total, 6)
        for state, score in sorted(bounded.items(), key=lambda item: item[1], reverse=True)
        if score > 0
    }


def _signal(scores: dict[str, float], reason: str) -> StateSignal:
    """점수 맵에서 대표 상태를 계산해 `StateSignal`로 묶는다."""

    normalized = _normalize_scores(scores)
    top_state, top_score = next(iter(normalized.items()))
    return StateSignal(
        scores=normalized,
        top_state=top_state,
        top_score=round(top_score, 6),
        reason=reason,
    )


def analyze_text_state(transcript: str) -> StateSignal:
    """STT 텍스트를 기반으로 상태 점수를 계산한다.

    현재는 키워드 휴리스틱을 사용하며, 강한 신호가 없으면 `calm`을 기본값으로 둔다.
    `tense`는 보조 축으로 취급하기 때문에 다른 상태보다 가중치를 약하게 준다.
    """

    text = transcript.strip()
    raw_scores = {state: 0.0 for state in STATE_ORDER}
    raw_scores["calm"] = 0.7

    for state, keywords in TEXT_STATE_KEYWORDS.items():
        for keyword in keywords:
            if keyword in text:
                increment = 0.35 if state == "tense" else 1.0
                raw_scores[state] += increment

    if any(keyword in text for keyword in TEXT_STATE_KEYWORDS["anxious"]):
        raw_scores["tense"] += 0.5
    if any(keyword in text for keyword in TEXT_STATE_KEYWORDS["low"]):
        raw_scores["calm"] -= 0.2
    if any(keyword in text for keyword in TEXT_STATE_KEYWORDS["engaged"]):
        raw_scores["calm"] -= 0.1

    if max(raw_scores.values()) <= 0.4:
        reason = "뚜렷한 감정 키워드가 없어 텍스트만 보면 calm에 가깝다고 판단했습니다."
    else:
        strongest = max(
            (state for state in STATE_ORDER if state != "calm"),
            key=lambda state: raw_scores[state],
        )
        reason = f"텍스트에서 `{strongest}` 관련 표현이 상대적으로 많이 감지됐습니다."
    return _signal(raw_scores, reason)


def analyze_voice_state(features: AudioFeatures) -> StateSignal:
    """음성 특징을 기반으로 prosody 상태 점수를 계산한다.

    침묵 비율, 에너지, pitch 평균/분산 같은 특징을 조합해
    차분함, 긴장감, 처짐, 몰입도를 휴리스틱으로 추정한다.
    """

    raw_scores = {state: 0.0 for state in STATE_ORDER}
    raw_scores["calm"] = 0.35

    if features.pitch_mean_hz and features.pitch_mean_hz > 210:
        raw_scores["anxious"] += 0.5
        raw_scores["tense"] += 0.3
    if features.pitch_std_hz > 45:
        raw_scores["anxious"] += 0.5
    if features.silence_ratio > 0.45:
        raw_scores["low"] += 0.6
        raw_scores["calm"] += 0.1
    if features.rms_mean < 0.03 and features.silence_ratio > 0.35:
        raw_scores["low"] += 0.4
    if features.silence_ratio < 0.2 and features.speech_rate_proxy > 0.18:
        raw_scores["tense"] += 0.5
    if features.rms_mean < 0.035 and features.pitch_std_hz < 25 and features.silence_ratio < 0.35:
        raw_scores["calm"] += 0.7
    if 0.03 <= features.rms_mean <= 0.06 and 10 <= features.pitch_std_hz < 28:
        raw_scores["engaged"] += 0.25

    reason_parts: list[str] = []
    if raw_scores["calm"] >= 0.8:
        reason_parts.append("에너지와 피치 변동이 작아 차분한 톤으로 보입니다")
    if raw_scores["low"] >= 0.5:
        reason_parts.append("침묵 비율이 높고 에너지가 낮아 처진 신호가 있습니다")
    if raw_scores["anxious"] >= 0.5:
        reason_parts.append("피치 평균 또는 변동폭이 커 긴장 신호가 있습니다")
    if not reason_parts:
        reason_parts.append("강한 prosody 이상치는 적어 비교적 안정적으로 보입니다")

    return _signal(raw_scores, ". ".join(reason_parts) + ".")


def fuse_signals(text_signal: StateSignal, voice_signal: StateSignal) -> StateSignal:
    """텍스트 신호와 음성 신호를 결합해 최종 상태를 계산한다.

    현재는 텍스트를 주 신호로 보고 음성을 보조 신호로 사용한다.
    두 신호가 같은 방향을 가리킬 때는 작은 보정치를 더해 최종 상태를 안정화한다.
    """

    weights = {"text": 0.65, "voice": 0.35}
    fused_scores = {state: 0.0 for state in STATE_ORDER}

    for state, score in text_signal.scores.items():
        fused_scores[state] += score * weights["text"]
    for state, score in voice_signal.scores.items():
        fused_scores[state] += score * weights["voice"]

    if text_signal.top_state == "calm" and voice_signal.top_state in {"calm", "tense"}:
        fused_scores["calm"] += 0.1
    if text_signal.top_state == "low" and voice_signal.top_state in {"low", "calm"}:
        fused_scores["low"] += 0.08
    if text_signal.top_state == "anxious" and voice_signal.top_state in {"anxious", "tense"}:
        fused_scores["anxious"] += 0.08
    if text_signal.top_state == "engaged" and voice_signal.top_state in {"calm", "engaged"}:
        fused_scores["engaged"] += 0.05

    reason = (
        f"텍스트는 `{text_signal.top_state}`, 음성은 `{voice_signal.top_state}`로 해석되어 "
        "텍스트를 주 신호로 두고 음성 특징으로 보정했습니다."
    )
    return _signal(fused_scores, reason)


def analyze_interview_state(audio_file_path: str, transcript: str) -> InterviewStateResult:
    """답변 음성과 STT 텍스트를 함께 받아 인터뷰 상태를 종합 분석한다.

    이 함수는 내부 상세 결과를 만드는 진입점이며,
    파이프라인에서는 이 결과를 기반으로 DB 저장용 payload와
    LLM 참고용 payload를 각각 만들어 사용한다.
    """

    started = time.perf_counter()
    audio_path = Path(audio_file_path)
    features = extract_audio_features(audio_path)
    text_signal = analyze_text_state(transcript)
    voice_signal = analyze_voice_state(features)
    fused_signal = fuse_signals(text_signal, voice_signal)
    elapsed = time.perf_counter() - started

    logger.info(
        (
            "[INTERVIEW_STATE] completed in %.3fs path=%s text_top=%s "
            "voice_top=%s fused_top=%s fused_scores=%s "
            "silence_ratio=%.3f pitch_mean_hz=%.3f pitch_std_hz=%.3f"
        ),
        elapsed,
        audio_file_path,
        text_signal.top_state,
        voice_signal.top_state,
        fused_signal.top_state,
        fused_signal.scores,
        features.silence_ratio,
        features.pitch_mean_hz,
        features.pitch_std_hz,
    )

    return InterviewStateResult(
        transcript=transcript,
        audio_features=features,
        text_signal=text_signal,
        voice_signal=voice_signal,
        fused_signal=fused_signal,
    )


def build_interview_state_db_payload(
    answer_id: int,
    interview_state: InterviewStateResult,
) -> InterviewStateDbPayload:
    """상세 분석 결과에서 DB 저장용 최소 payload만 추려낸다."""

    return InterviewStateDbPayload(
        answer_id=answer_id,
        top_state=interview_state.fused_signal.top_state,
        fused_scores=interview_state.fused_signal.scores,
        audio_features=interview_state.audio_features,
    )


def _format_scores(scores: dict[str, float]) -> str:
    """프롬프트에 넣기 쉽게 상태 점수 맵을 한 줄 문자열로 정리한다."""

    return ", ".join(f"{state}={scores.get(state, 0.0):.2f}" for state in STATE_ORDER)


def build_interview_state_prompt_payload(
    interview_state: InterviewStateResult,
) -> InterviewStatePromptPayload:
    """상세 분석 결과를 LLM 참고용 간단한 상태 문구로 변환한다.

    현재 설계의 핵심은 해석을 강하게 주입하지 않는 것이다.
    따라서 `top_state`와 `fused_scores`를 참고용 수치로만 보여주고,
    절대 판단이 아니라는 안내 문구를 함께 포함한다.
    """

    top_state = interview_state.fused_signal.top_state
    fused_scores = {
        state: round(interview_state.fused_signal.scores.get(state, 0.0), 6)
        for state in STATE_ORDER
    }
    formatted_prompt = (
        "[인터뷰 상태 참고]\n"
        "다음 점수는 답변 음성과 텍스트를 바탕으로 계산된 참고용 상태 신호입니다. "
        "절대 판단이 아니라 후속 질문의 톤과 깊이를 조절하는 보조 정보로만 활용하세요.\n"
        f"- top_state: {top_state}\n"
        f"- fused_scores: {_format_scores(fused_scores)}"
    )
    return InterviewStatePromptPayload(
        top_state=top_state,
        fused_scores=fused_scores,
        formatted_prompt=formatted_prompt,
    )
