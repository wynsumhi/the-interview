from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
import soundfile as sf

FRAME_MS = 25
HOP_MS = 10

STATE_ORDER = (
    "calm",
    "tense",
    "anxious",
    "low",
    "engaged",
)

TEXT_STATE_KEYWORDS = {
    "anxious": (
        "긴장",
        "불안",
        "떨",
        "걱정",
        "신경 쓰",
        "부담",
        "초조",
        "무섭",
    ),
    "low": (
        "우울",
        "슬프",
        "허무",
        "힘들",
        "지치",
        "외롭",
        "공허",
        "무기력",
        "속상",
    ),
    "engaged": (
        "좋았",
        "재밌",
        "흥미",
        "의미",
        "몰입",
        "재미있",
        "인상적",
        "즐거",
        "반갑",
    ),
    "tense": (
        "바쁘",
        "피곤",
        "압박",
        "정신없",
        "예민",
    ),
}


@dataclass(frozen=True)
class AudioFeatures:
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
    scores: dict[str, float]
    top_state: str
    top_score: float
    reason: str


@dataclass(frozen=True)
class InterviewStateResult:
    transcript: str
    audio_features: AudioFeatures
    text_signal: StateSignal
    voice_signal: StateSignal
    fused_signal: StateSignal


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="PoC for interview-state analysis using transcript + prosody under tests/."
    )
    parser.add_argument("--audio", required=True, help="Path to wav audio file.")
    parser.add_argument("--transcript", required=True, help="Transcript text for the audio.")
    parser.add_argument(
        "--output",
        default=str(Path("tests") / "models" / "artifacts" / "interview_state_result.json"),
        help="Path to save JSON output.",
    )
    return parser.parse_args()


def read_audio(audio_path: Path) -> tuple[np.ndarray, int]:
    samples, sample_rate = sf.read(audio_path)
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    return samples.astype(np.float32), sample_rate


def frame_signal(samples: np.ndarray, sample_rate: int) -> np.ndarray:
    frame_length = max(1, int(sample_rate * FRAME_MS / 1000))
    hop_length = max(1, int(sample_rate * HOP_MS / 1000))
    if len(samples) < frame_length:
        pad = np.zeros(frame_length - len(samples), dtype=np.float32)
        samples = np.concatenate([samples, pad])
    frame_count = 1 + max(0, (len(samples) - frame_length) // hop_length)
    frames = np.lib.stride_tricks.sliding_window_view(samples, frame_length)[::hop_length]
    return frames[:frame_count].copy()


def compute_rms(frames: np.ndarray) -> np.ndarray:
    return np.sqrt(np.mean(np.square(frames), axis=1) + 1e-12)


def compute_zcr(frames: np.ndarray) -> np.ndarray:
    signs = np.signbit(frames)
    crossings = np.sum(signs[:, 1:] != signs[:, :-1], axis=1)
    return crossings / np.maximum(1, frames.shape[1] - 1)


def estimate_pitch(frame: np.ndarray, sample_rate: int) -> float:
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
    samples, sample_rate = read_audio(audio_path)
    frames = frame_signal(samples, sample_rate)
    rms = compute_rms(frames)
    zcr = compute_zcr(frames)

    silence_threshold = max(0.01, float(np.percentile(rms, 25) * 0.8))
    silence_mask = rms < silence_threshold
    voiced_candidates = frames[rms >= max(silence_threshold, 0.015)]
    pitch_values = np.array([estimate_pitch(frame, sample_rate) for frame in voiced_candidates])
    pitch_values = pitch_values[pitch_values > 0]

    duration_sec = len(samples) / sample_rate if sample_rate > 0 else 0.0
    voiced_ratio = 1.0 - float(np.mean(silence_mask))
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
    normalized = _normalize_scores(scores)
    top_state, top_score = next(iter(normalized.items()))
    return StateSignal(
        scores=normalized,
        top_state=top_state,
        top_score=round(top_score, 6),
        reason=reason,
    )


def analyze_text_state(transcript: str) -> StateSignal:
    text = transcript.strip()
    raw_scores = {state: 0.0 for state in STATE_ORDER}
    raw_scores["calm"] = 0.7

    for state, keywords in TEXT_STATE_KEYWORDS.items():
        for keyword in keywords:
            if keyword in text:
                increment = 1.0
                if state == "tense":
                    increment = 0.35
                raw_scores[state] += increment

    if any(keyword in text for keyword in TEXT_STATE_KEYWORDS["anxious"]):
        raw_scores["tense"] += 0.5
    if any(keyword in text for keyword in TEXT_STATE_KEYWORDS["low"]):
        raw_scores["calm"] -= 0.2
    if any(keyword in text for keyword in TEXT_STATE_KEYWORDS["engaged"]):
        raw_scores["calm"] -= 0.1

    if max(raw_scores.values()) <= 0.4:
        reason = "강한 상태 키워드가 적어 텍스트만 보면 calm에 가깝다고 판단함."
    else:
        strongest = max(
            (state for state in STATE_ORDER if state != "calm"),
            key=lambda state: raw_scores[state],
        )
        reason = f"텍스트에서 `{strongest}` 관련 표현이 상대적으로 많이 감지됨."
    return _signal(raw_scores, reason)


def analyze_voice_state(features: AudioFeatures) -> StateSignal:
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
    if features.silence_ratio < 0.2 and features.speech_rate_proxy > 0.18:
        raw_scores["tense"] += 0.5
    if features.rms_mean < 0.035 and features.pitch_std_hz < 25 and features.silence_ratio < 0.35:
        raw_scores["calm"] += 0.7
    if features.rms_mean < 0.03 and features.silence_ratio > 0.35:
        raw_scores["low"] += 0.4
    if 0.03 <= features.rms_mean <= 0.06 and 10 <= features.pitch_std_hz < 28:
        raw_scores["engaged"] += 0.25

    reason_parts: list[str] = []
    if raw_scores["calm"] >= 0.8:
        reason_parts.append("에너지와 피치 변동이 크지 않아 차분한 톤으로 보임")
    if raw_scores["low"] >= 0.5:
        reason_parts.append("침묵 비율이 높고 에너지가 낮아 침체 신호가 있음")
    if raw_scores["anxious"] >= 0.5:
        reason_parts.append("피치 평균 또는 변동폭이 커 긴장 신호가 있음")
    if not reason_parts:
        reason_parts.append("강한 prosody 이상치가 적어 비교적 평이한 톤으로 보임")

    return _signal(raw_scores, ". ".join(reason_parts) + ".")


def fuse_signals(text_signal: StateSignal, voice_signal: StateSignal) -> StateSignal:
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
        f"텍스트는 `{text_signal.top_state}`, 음성은 `{voice_signal.top_state}`로 해석됨. "
        "텍스트 의미를 주 신호로 두고 prosody를 보정 신호로 반영함."
    )
    return _signal(fused_scores, reason)


def run_interview_state_experiment(audio_path: Path, transcript: str) -> InterviewStateResult:
    features = extract_audio_features(audio_path)
    text_signal = analyze_text_state(transcript)
    voice_signal = analyze_voice_state(features)
    fused_signal = fuse_signals(text_signal, voice_signal)
    return InterviewStateResult(
        transcript=transcript,
        audio_features=features,
        text_signal=text_signal,
        voice_signal=voice_signal,
        fused_signal=fused_signal,
    )


def main() -> None:
    args = parse_args()
    audio_path = Path(args.audio)
    if not audio_path.exists():
        raise SystemExit(f"audio file not found: {audio_path}")

    result = run_interview_state_experiment(audio_path, args.transcript)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(asdict(result), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"text     : {result.text_signal.top_state} ({result.text_signal.top_score:.4f})")
    print(f"voice    : {result.voice_signal.top_state} ({result.voice_signal.top_score:.4f})")
    print(f"combined : {result.fused_signal.top_state} ({result.fused_signal.top_score:.4f})")
    print(f"saved    : {output_path}")


if __name__ == "__main__":
    main()
